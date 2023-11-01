package io.hyperfoil.tools.horreum.dev.services.runtime.dev;

public class HorreumDevInfo {

    private int numberOfTests = 0;
    private String loaded = "false";


    public int getNumberOfTests(){
        return numberOfTests;
    }

    public String getIsLoaded(){
        return loaded;
    }
}
