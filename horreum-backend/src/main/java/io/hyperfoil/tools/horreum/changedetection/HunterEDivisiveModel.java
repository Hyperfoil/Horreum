package io.hyperfoil.tools.horreum.changedetection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.hyperfoil.tools.horreum.api.data.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@ApplicationScoped
public class HunterEDivisiveModel implements ChangeDetectionModel {
    private static final Logger log = Logger.getLogger(HunterEDivisiveModel.class);
    public static final String HUNTER_CONFIG = "HUNTER_CONFIG";
    private static String[] HEADERS = {"kpi", "timestamp", "datasetid"};

    private static final Pattern datapointPattern = Pattern.compile("(?<timestamp>^\\d{4}-[01]\\d-[0-3]\\d\\s[0-2]\\d:[0-5]\\d:[0-5]\\d)\\s[+|-]\\d{4}\\s+(?<dataPointId>\\d+)\\s+(?<kpi>\\d+?\\.?\\d+)$");

    @Override
    public ConditionConfig config() {
        ConditionConfig conditionConfig = new ConditionConfig(ChangeDetectionModelType.names.EDIVISIVE, "eDivisive - Hunter", "This model uses the Hunter eDivisive algorithm to determine change points in a continual series.");
        conditionConfig.defaults.put("model", new TextNode(ChangeDetectionModelType.names.EDIVISIVE));

        return conditionConfig;
    }

    @Override
    public ChangeDetectionModelType type() {
        return ChangeDetectionModelType.EDIVISIVE;
    }

    @Override
    public void analyze(List<DataPointDAO> dataPoints, JsonNode configuration, Consumer<ChangeDAO> changeConsumer) throws ChangeDetectionException {

        TmpFiles tmpFiles = null;

        try {
            try {
                tmpFiles = TmpFiles.instance();
            } catch (IOException e) {
                String errMsg = "Could not create temporary file for Hunter eDivisive algorithm";
                log.error(errMsg, e);
                throw new ChangeDetectionException(errMsg, e);
            }

            try (final FileWriter fw = new FileWriter(tmpFiles.inputFile, true);
                 final PrintWriter pw = new PrintWriter(fw);) {

                Collections.reverse(dataPoints);

                //write out csv fields
                pw.println(Arrays.stream(HEADERS).collect(Collectors.joining(",")));
                dataPoints.forEach(dataPointDAO -> pw.println(String.format("%.2f,%s,%d", dataPointDAO.value, dataPointDAO.timestamp.toString(), dataPointDAO.id)));

            } catch (IOException e) {
                String errMsg = "Could not create file writer for Hunter eDivisive algorithm";
                log.error(errMsg, e);
                throw new ChangeDetectionException(errMsg, e);
            }

            log.debugf("created csv output : %s", tmpFiles.inputFile.getAbsolutePath());

            if (!validateInputCsv(tmpFiles)) {
                String errMsg = String.format("could not validate: %s", tmpFiles.inputFile);
                log.error(errMsg);
                throw new ChangeDetectionException(errMsg);
            }

            DataPointDAO firstDatapoint = dataPoints.get(0);

            processChangePoints(
                    (dataPointID) -> dataPoints.stream().filter(dataPoint -> dataPoint.id.equals(dataPointID)).findFirst(),
                    changeConsumer,
                    tmpFiles,
                    firstDatapoint.timestamp
            );
        } finally {
            if (tmpFiles != null) {
                cleanupTmpFiles(tmpFiles);
            }
        }
    }

    protected void cleanupTmpFiles(TmpFiles tmpFiles) {
        if( tmpFiles.tmpdir.exists() ) {
            clearDir(tmpFiles.tmpdir);
        } else {
            log.debugf("Trying to cleanup temp files, but they do not exist!");
        }
    }


    private void clearDir(File dir){
        Arrays.stream(dir.listFiles()).forEach(file -> {
            if ( file.isDirectory() ){
                clearDir(file);
            }
            file.delete();
        });
        if(!dir.delete()){
            log.errorf("Failed to cleanup up temporary files: %s", dir.getAbsolutePath());
        }
    }

    protected void processChangePoints(Function<Integer, Optional<DataPointDAO>> changePointSupplier, Consumer<ChangeDAO> changeConsumer, TmpFiles tmpFiles, Instant sinceInstance) {
        String command = "hunter analyze horreum --since '" + sinceInstance.toString() + "'";
        log.debugf("Running command: %s", command);


        List<String> results = executeProcess(tmpFiles, false, "bash", "-l", "-c", command);

        /*
            We are parsing the result file from Hunter, which has the following format;
                INFO: Computing change points for test horreum...
                time                         datasetid    kpi
                -------------------------  -----------  -----
                2024-04-27 06:55:06 +0000            1      1
                ...
                2024-04-27 06:55:06 +0000            8      2
                2024-04-27 06:55:06 +0000            9      2
                                                        ·····
                                                        +542.9%
                                                        ·····
                2024-04-27 06:55:06 +0000           10     10
        */
        //if there are no results, the file will contain only the header
        if (results.size() > 3) {
            Iterator<String> resultIter = results.iterator();
            while (resultIter.hasNext()) {
                String line = resultIter.next();
                //change points are denoted by a series of '··' characters
                //the line after the '··' contains the change point details
                if (line.contains("··")) {

                    String change = resultIter.next().trim();
                    resultIter.next(); // skip line after the change details containing '··'
                    String changeDetails = resultIter.next(); //the next line is the datapoint that the change was detected for

                    Matcher foundChange = datapointPattern.matcher(changeDetails);

                    if ( foundChange.matches() ){
                        String timestamp = foundChange.group("timestamp");
                        Integer datapointID = Integer.parseInt(foundChange.group("dataPointId"));

                        log.debugf("Found change point `%s` at `%s` for dataset: %d", change, timestamp, datapointID);

                        Optional<DataPointDAO> foundDataPoint = changePointSupplier.apply(datapointID);

                        if (foundDataPoint.isPresent()) {
                            ChangeDAO changePoint = ChangeDAO.fromDatapoint(foundDataPoint.get());

                            changePoint.description = String.format("eDivisive change `%s` at `%s` for dataset: %d", change, timestamp, datapointID);

                            log.trace(changePoint.description);
                            changeConsumer.accept(changePoint);
                        } else {
                            log.errorf("Could not find datapoint (%d) in set!", datapointID);
                        }
                    } else {
                        log.errorf("Could not parse hunter line: '%s'", changeDetails);
                    }
                }
            }

        } else {
            log.debugf("No change points were detected in : %s", tmpFiles.tmpdir.getAbsolutePath());
        }
    }

    protected boolean validateInputCsv(TmpFiles tmpFiles) {
        executeProcess(tmpFiles, true, "bash", "-l", "-c", "hunter validate");

        try(FileReader fileReader = new FileReader(tmpFiles.logFile);
            BufferedReader reader = new BufferedReader(fileReader);){

            Optional<String> optLine = reader.lines().filter(line -> line.contains("Validation finished")).findFirst();
            if(optLine.isEmpty()) {
                log.errorf("Could not validate: %s", tmpFiles.tmpdir.getAbsolutePath());
                return false;
            }
            if( optLine.get().contains("INVALID") ) {
                log.errorf("Invalid format for: %s; see log for details: %s", tmpFiles.tmpdir.getAbsolutePath(), tmpFiles.logFile.getAbsolutePath());
                return false;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public ModelType getType() {
        return ModelType.BULK;
    }


    protected static class TmpFiles {
        final File inputFile;
        final File tmpdir;
        final File confFile;
        final File logFile;

        public static TmpFiles instance() throws IOException {
            return new TmpFiles();
        }

        public TmpFiles() throws IOException {
            tmpdir = Files.createTempDirectory("hunter").toFile();

            Path respourcesPath = Path.of(tmpdir.getAbsolutePath(), "tests", "resources");
            Files.createDirectories(respourcesPath);
            inputFile = Path.of(respourcesPath.toFile().getAbsolutePath(), "horreum.csv").toFile();

            confFile = Path.of(respourcesPath.toFile().getAbsolutePath(), "hunter.yaml").toFile();
            logFile = Path.of(respourcesPath.toFile().getAbsolutePath(), "hunter.log").toFile();

            try (InputStream confInputStream = HunterEDivisiveModel.class.getClassLoader().getResourceAsStream("changeDetection/hunter.yaml")) {


                try( OutputStream confOut = new FileOutputStream(confFile)){
                    confOut.write(confInputStream.readAllBytes());
                } catch (IOException e){
                    log.error("Could not extract Hunter configuration from archive");
                }

            } catch (IOException e) {
                log.error("Could not create temporary file for Hunter eDivisive algorithm", e);
            }

        }
    }


    protected List<String> executeProcess( TmpFiles tmpFiles, boolean redirectOutput,  String... command){
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Map<String, String> env = processBuilder.environment();

        env.put(HUNTER_CONFIG, tmpFiles.confFile.getAbsolutePath());
        processBuilder.directory(tmpFiles.tmpdir);

        processBuilder.redirectErrorStream(redirectOutput);
        if(redirectOutput)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(tmpFiles.logFile));

        Process process = null;
        try {
            process = processBuilder.start();
            List<String> results = readOutput(process.getInputStream());
            int exitCode = process.waitFor();

            if ( exitCode != 0 ){
                log.errorf("Hunter process failed with exit code: %d", exitCode);
                log.errorf("See error log for details: %s", tmpFiles.logFile.getAbsolutePath());
                return null;
            }

            return results;

        } catch (IOException | InterruptedException e) {
            if (process != null ) {
                process.destroy();
            }
            throw new RuntimeException(e);
        }
    }

    private List<String> readOutput(InputStream inputStream) throws IOException {
        try (BufferedReader output = new BufferedReader(new InputStreamReader(inputStream))) {
            return output.lines()
                    .collect(Collectors.toList());
        }
    }
}
