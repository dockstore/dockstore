package io.dockstore.client.cli;

import org.junit.Test;

import java.io.IOException;

import static io.dockstore.client.cli.ArgumentUtility.CWL_STRING;
import static io.dockstore.client.cli.ArgumentUtility.WDL_STRING;

public class LaunchTestIT {
    //create tests that will call client.checkEntryFile for workflow launch with different files and descriptor

    public static String checkEntryFile(String ext, String content, String descriptor){
        // this method is based on targetClient.checkEntryFile, but in here we just want to check if the basic output is correct

        /* Keyword:
        * launchCWL, launchWDL               -->launching cwl or wdl
        * descriptor                         --> ask user to input descriptor
        * invalidCWL, invalid WDL            --> not a valid cwl/wdl file
        * warningLaunchCWL, warningLaunchWDL --> output warning to add extension, but still launch wdl/cwl
        * error                              --> invalid entry file (content and/or name) */

        if(ext.equals("cwl")){
            if(content.equals("cwl")) {
                return "launchCWL";
            }else if(!content.equals("cwl") && descriptor.equals("")){
                return "descriptor";
            }else if(!content.equals("cwl") && descriptor.equals(CWL_STRING)){
                return "invalidCWL";
            }else if(content.equals("wdl") && descriptor.equals(WDL_STRING)){
                return "warningLaunchWDL";
            }
        }else if(ext.equals("wdl")){
            if(content.equals("wdl")){
                return "launchWDL";
            }else if(!content.equals("wdl") && descriptor.equals("")){
                return "descriptor";
            }else if(!content.equals("wdl") && descriptor.equals(WDL_STRING)){
                return "invalidWDL";
            }else if(content.equals("cwl") && descriptor.equals(CWL_STRING)){
                return "warningLaunchCWL";
            }
        }else if(ext.equals("")){
            if(content.equals("cwl")){
                return "warningLaunchCWL";
            }else if(content.equals("wdl")){
                return "warningLaunchWDL";
            }
        }
        return "error";
    }

    @Test
    public void wdlCorrect() throws IOException{
        //Test when content and extension are wdl  --> no need descriptor
        String result = checkEntryFile("wdl","wdl","");
        assert(result.equals("launchWDL"));
    }

    @Test
    public void cwlCorrect() throws IOException{
        //Test when content and extension are cwl  --> no need descriptor
        String result = checkEntryFile("cwl","cwl","");
        assert(result.equals("launchCWL"));
    }

    @Test
    public void cwlWrongExt() throws IOException{
        //Test when content = cwl but ext = wdl, ask for descriptor
        String result = checkEntryFile("wdl","cwl","");
        assert(result.equals("descriptor"));
    }

    @Test
    public void wdlWrongExt() throws IOException{
        //Test when content = wdl but ext = cwl, ask for descriptor
        String result = checkEntryFile("cwl","wdl","");
        assert(result.equals("descriptor"));
    }

    @Test
    public void randomExtCwl() throws IOException{
        //Test when content is random, but ext = cwl
        String result = checkEntryFile("cwl","","");
        assert(result.equals("descriptor"));
    }

    @Test
    public void randomExtWdl() throws IOException{
        //Test when content is random, but ext = wdl
        String result = checkEntryFile("wdl","","");
        assert(result.equals("descriptor"));
    }

    @Test
    public void cwlWrongExtForce() throws IOException{
        //Test when content = cwl but ext = wdl, descriptor provided --> CWL
        String result = checkEntryFile("wdl","cwl",CWL_STRING);
        assert(result.equals("warningLaunchCWL"));
    }

    @Test
    public void wdlWrongExtForce() throws IOException{
        //Test when content = wdl but ext = cwl, descriptor provided --> WDL
        String result = checkEntryFile("cwl","wdl",WDL_STRING);
        assert(result.equals("warningLaunchWDL"));
    }

    @Test
    public void cwlWrongExtForce1() throws IOException{
        //Test when content = cwl but ext = wdl, descriptor provided --> !CWL
        String result = checkEntryFile("wdl","cwl",WDL_STRING);
        assert(result.equals("invalidWDL"));
    }

    @Test
    public void wdlWrongExtForce1() throws IOException{
        //Test when content = wdl but ext = cwl, descriptor provided --> !WDL
        String result = checkEntryFile("cwl","wdl",CWL_STRING);
        assert(result.equals("invalidCWL"));
    }

    @Test
    public void cwlNoExt() throws IOException{
        //Test when content = cwl but no ext
        String result = checkEntryFile("","cwl","");
        assert(result.equals("warningLaunchCWL"));

    }

    @Test
    public void wdlNoExt() throws IOException{
        //Test when content = wdl but no ext
        String result = checkEntryFile("","wdl","");
        assert(result.equals("warningLaunchWDL"));

    }

    @Test
    public void randomNoExt() throws IOException{
        //Test when content is neither CWL nor WDL, and there is no extension
        String result = checkEntryFile("","","");
        assert(result.equals("error"));
    }

}
