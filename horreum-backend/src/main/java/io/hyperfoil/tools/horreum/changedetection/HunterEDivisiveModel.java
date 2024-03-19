package io.hyperfoil.tools.horreum.changedetection;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.hyperfoil.tools.horreum.api.data.ConditionConfig;
import io.hyperfoil.tools.horreum.api.data.changeDetection.ChangeDetectionModelType;
import io.hyperfoil.tools.horreum.entity.alerting.ChangeDAO;
import io.hyperfoil.tools.horreum.entity.alerting.DataPointDAO;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class HunterEDivisiveModel implements ChangeDetectionModel {
    private static final Logger log = Logger.getLogger(HunterEDivisiveModel.class);
    public static final String NAME = "hunterEDivisive";
    private static String[] HEADERS = {"kpi", "timestamp", "datasetid"};

    @Override
    public ConditionConfig config() {
        ConditionConfig conditionConfig = new ConditionConfig(NAME, "eDivisive - Hunter", "This model uses the Hunter eDivisive algorithm to determine change points in a continual series.");
        conditionConfig.defaults.put("model", new TextNode(NAME));

        return conditionConfig;
    }

    @Override
    public ChangeDetectionModelType type() {
        return ChangeDetectionModelType.EDIVISIVE_HUNTER;
    }

    @Override
    public void analyze(List<DataPointDAO> dataPoints, JsonNode configuration, Consumer<ChangeDAO> changeConsumer) {

        TmpFiles tmpFiles = null;

        try {
            try {
                tmpFiles = TmpFiles.instance();
            } catch (IOException e) {
                log.error("Could not create temporary file for Hunter eDivisive algorithm", e);
                return;
            }

            assert tmpFiles.inputFile != null;

            try (final FileWriter fw = new FileWriter(tmpFiles.inputFile, true);
                 final PrintWriter pw = new PrintWriter(fw);) {

                CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                        .setHeader(HEADERS)
                        .build();

                try (final CSVPrinter printer = new CSVPrinter(pw, csvFormat)) {
                    Collections.reverse(dataPoints);
                    dataPoints.forEach(dataPointDAO -> {
                        try {
                            printer.printRecord(String.format("%.2f", dataPointDAO.value), dataPointDAO.timestamp.toEpochMilli() / 1000, dataPointDAO.id);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            } catch (IOException e) {
                log.error("Could not create file writer for Hunter eDivisive algorithm", e);
                return;
            }

            log.debugf("created csv output : %s", tmpFiles.inputFile.getAbsolutePath());

            if (!validateInputCsv(tmpFiles)) {
                log.errorf("could not validate: %s", tmpFiles.inputFile);
                return;
            }

            processChangePoints(
                    (dataPointID) -> dataPoints.stream().filter(dataPoint -> dataPoint.id.equals(dataPointID)).findFirst(),
                    changeConsumer,
                    tmpFiles
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

    protected void processChangePoints(Function<Integer, Optional<DataPointDAO>> changePointSupplier, Consumer<ChangeDAO> changeConsumer, TmpFiles tmpFiles) {
        //TODO:: figure out how to define the since parameter - from min datapoint timestamp
        List<String> results = executeProcess(tmpFiles, false, "hunter", "analyze", "horreum", "--since", "'10 years ago'");

        if (results.size() > 3) {
            Iterator<String> resultIter = results.iterator();
            while (resultIter.hasNext()) {
                String line = resultIter.next();
                if (line.contains("··")) { //this is dynamic depending on the number of significant figures from the metric, we print it to 2sf, so 2x '.' should suffice

                    String change = resultIter.next().trim();
                    resultIter.next(); // skip line containing '··'
                    String[] changeDetails = resultIter.next().split("  ");
                    String timestamp = changeDetails[0];
                    Integer datapointID = Integer.parseInt(changeDetails[3]);

                    log.infof("Found changepoint `%s` at `%s` for dataset: %d", change, timestamp, datapointID);

                    Optional<DataPointDAO> foundDataPoint = changePointSupplier.apply(datapointID);

                    if (foundDataPoint.isPresent()) {
                        ChangeDAO changePoint = ChangeDAO.fromDatapoint(foundDataPoint.get());

                        //TODO: this should be templated
                        changePoint.description = String.format("eDivisive change `%s` at `%s` for dataset: %d", change, timestamp, datapointID);

                        log.trace(changePoint.description);
                        changeConsumer.accept(changePoint);
                    } else {
                        log.errorf("Could not find datapoint in set!");
                    }
                }
            }

        } else {
            log.debugf("No change points were detected in : %s", tmpFiles.tmpdir.getAbsolutePath());
        }
    }

    protected boolean validateInputCsv(TmpFiles tmpFiles) {
        executeProcess(tmpFiles, true, "hunter", "validate");
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
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    @Override
    public ModelType getType() {
        return ModelType.BULK;
    }


    static class TmpFiles {
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

        env.put("HUNTER_CONFIG", tmpFiles.confFile.getAbsolutePath());
        processBuilder.directory(tmpFiles.tmpdir);

        processBuilder.redirectErrorStream(redirectOutput);
        if(redirectOutput)
            processBuilder.redirectOutput(ProcessBuilder.Redirect.appendTo(tmpFiles.logFile));

        Process process;
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
