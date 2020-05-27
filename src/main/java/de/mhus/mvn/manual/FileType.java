package de.mhus.mvn.manual;

import org.apache.maven.plugins.annotations.Parameter;

public class FileType {

	public FileType() {}
	
	public FileType(String extension) {
		this.extension = extension;
	}
	
	@Parameter public String extension = null;
	
    @Parameter public String blockStart = "/*#";

    @Parameter public String blockEnd = "*/";

    @Parameter public String blockHeader = "*#";

    @Parameter public String blockLine = "*";

    @Parameter public String[] blockIgnore = new String[0];

    public String toString() {
    	return extension + " -> [" + blockStart + "," + blockEnd + "," + blockHeader + "]";
    }
}
