package io.dockstore.common;

/**
 * Created by aduncan on 11/02/16.
 * This contains all the compatible descriptor file types
 */
public enum Descriptors {
        CWL("cwl"), WDL("wdl");

        private String descriptor;

        Descriptors(String descriptor) {
                this.descriptor = descriptor;
        }

        @Override
        public String toString() {
                return descriptor;
        }

}
