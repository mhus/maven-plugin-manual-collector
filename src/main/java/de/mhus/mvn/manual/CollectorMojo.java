/**
 * Copyright 2018 Mike Hummel
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mhus.mvn.manual;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Date;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import de.mhus.lib.core.MCast;
import de.mhus.lib.core.MCollection;
import de.mhus.lib.core.MDate;
import de.mhus.lib.core.MFile;
import de.mhus.lib.core.MProperties;
import de.mhus.lib.core.MSystem;
import de.mhus.lib.core.logging.Log;

@Mojo(
        name = "collect",
        defaultPhase = LifecyclePhase.GENERATE_RESOURCES,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        inheritByDefault = false,
        threadSafe = false,
        requiresProject = true,
        requiresDirectInvocation = true,
        aggregator = true)
public class CollectorMojo extends AbstractMojo {

    private Log log = new MavenPluginLog(this);
    private long timestamp;

    @Parameter public FileType[] fileTypes = null;

    @Parameter public String[] start = new String[] {"src/main/java"};

    @Parameter public String[] exclude = new String[] {"bin", "target", "test"};

    @Parameter(defaultValue = "${project}")
    protected MavenProject project;

    @Parameter public String placeholderBegin = "{{";

    @Parameter public String placeholderEnd = "}}";

    @Parameter(required = true)
    public String outputDirectory;

    @Parameter public String outputExtension = "adoc";

    @Parameter public boolean cleanupOutputDirectory = false;

    @Parameter public boolean generateIndexFiles = false;

    @Parameter public String indexFileName = "index.adoc";

    @Parameter public String indexHeader = "";

    @Parameter public String indexFooter = "";

    @Parameter public String indexOrderBy = "sort";

    @Parameter public String indexLine = "include::{{_file}}[]";

    @Parameter public String textHeader = "";

    @Parameter public String textFooter = "";

    @Parameter public String rootDirectory = ".";

    @Parameter public boolean generateConcatFiles = false;
    
    @Parameter public String concatFileName = "concat.adoc";
    
    @Parameter public String concatHeader = "::toc::\n\n";
    
    @Parameter public String concatFooter = "";
    
    @Parameter public String concatOrderBy = "sort";
    
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
    	
    	getLog().debug("FileTypes: " + Arrays.toString(fileTypes));
    	
    	if (fileTypes == null)
    		fileTypes = new FileType[] { new FileType("java") };
    	
        timestamp = System.currentTimeMillis();
        File root = new File(rootDirectory);

        if (cleanupOutputDirectory) deleteOutputDirectory();

        findStart(root);

        if (generateIndexFiles) generateIndexFiles();
        
        if (generateConcatFiles) generateConcatFiles();

    }

    private void generateIndexFiles() {
        for (File dir : new File(outputDirectory).listFiles()) {
            if (dir.isDirectory() && !dir.getName().startsWith(".")) {
                generateIndexFile(dir);
            }
        }
    }

    private void generateIndexFile(File dir) {
        TreeMap<String, MProperties> list = new TreeMap<>();
        for (File file : dir.listFiles()) {
            if (file.isFile() && file.getName().endsWith("." + outputExtension)) {
                MProperties prop = MProperties.load(new File(file.getPath() + ".properties"));
                prop.setString("_file", file.getName());
                list.put(prop.getString(indexOrderBy, "") + "_" + file.getName(), prop);
            }
        }
        StringBuilder out = new StringBuilder().append(removeQuots(indexHeader)).append("\n");
        for (Entry<String, MProperties> entry : list.entrySet()) {
            String line = placeholdersManual(entry.getValue(), removeQuots(indexLine));
            out.append(line).append("\n");
        }
        out.append(removeQuots(indexFooter));
        File indexFile = new File(dir, indexFileName);
        log.i("index", indexFile);
        MFile.writeFile(indexFile, out.toString());
    }

    private void generateConcatFiles() {
        for (File dir : new File(outputDirectory).listFiles()) {
            if (dir.isDirectory() && !dir.getName().startsWith(".")) {
                generateConcatFile(dir);
            }
        }
    }

    private void generateConcatFile(File dir) {
        TreeMap<String, MProperties> list = new TreeMap<>();
        for (File file : dir.listFiles()) {
            if (file.isFile() && file.getName().endsWith("." + outputExtension)) {
                MProperties prop = MProperties.load(new File(file.getPath() + ".properties"));
                prop.setProperty("_file", file);
                list.put(prop.getString(concatOrderBy, "") + "_" + file.getName(), prop);
            }
        }
        
        File concatFile = new File(dir, concatFileName);
        log.i("concat", concatFile);
        try (FileWriter fw = new FileWriter(concatFile)) {
        	
        	fw.write(concatHeader.replace("\\n", "\n"));
        	
        	for (Entry<String, MProperties> entry : list.entrySet()) {
        		File file = (File) entry.getValue().getProperty("_file");
        		try (FileReader fr = new FileReader(file)) {
        			MFile.copyFile(fr, fw);
        		}
        		fw.write("\n\n");
        	}
        	
        	fw.write(concatFooter.replace("\\n", "\n"));
        	
        } catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        StringBuilder out = new StringBuilder().append(removeQuots(indexHeader)).append("\n");
        out.append(removeQuots(indexFooter));
        File indexFile = new File(dir, indexFileName);
        log.i("index", indexFile);
        MFile.writeFile(indexFile, out.toString());
    }
    
    private void deleteOutputDirectory() {
        File dir = new File(outputDirectory);
        log.i("delete", dir);
        MFile.deleteDir(dir);
        dir.mkdirs();
    }

    private void findStart(File dir) {
        log.d("findStart", dir);
        if (MCollection.contains(exclude, dir.getName())) {
            log.d("ignore", dir);
            return;
        }
        String dirPath = dir.getPath().replace('\\', '/');
        for (String s : start) {
            if (dirPath.endsWith(s)) {
                log.d("start", dir);
                parseDir(dir, dir);
                return;
            }
        }
        for (File d : dir.listFiles()) {
            if (d.isDirectory() && !d.getName().startsWith(".")) findStart(d);
        }
    }

    private void parseDir(File dir, File start) {
        log.d("parseDir", dir);
        for (File d : dir.listFiles()) {
            if (d.isDirectory() && !d.getName().startsWith(".")) parseDir(d, start);
            if (d.isFile() && !d.getName().startsWith(".")) {
                for (FileType fileType : fileTypes) {
                    if (d.getName().endsWith("." + fileType.extension)) {
                        parseFile(d, start, fileType);
                        continue;
                    }
                }
            }
        }
    }

    private void parseFile(File file, File start, FileType fileType) {
        log.d("parseFile", file);
        String content = MFile.readFile(file);
        int cnt = 0;
        while (true) {
            int begin = content.indexOf(fileType.blockStart);
            if (begin < 0) return;
            int end = content.indexOf(fileType.blockEnd, begin + 3);
            if (end < 0) {
                log.w("start without end token");
                return;
            }
            String part = content.substring(begin + fileType.blockStart.length(), end);
            content = content.substring(end + fileType.blockEnd.length());
            parseManual(part, file, start, cnt, fileType);
            cnt++;
        }
    }

    private void parseManual(String content, File file, File start, int cnt, FileType fileType) {
        log.d("parseManual", content);
        String[] lines = content.split("\n");
        MProperties prop = new MProperties();
        prop.setString("file.name", file.getName());
        prop.setString(
                "file.path", file.getAbsolutePath().substring(start.getAbsolutePath().length()));
        prop.setString("file.start", start.getPath());
        StringBuilder text = new StringBuilder().append(removeQuots(textHeader));
        boolean header = true;
        boolean first = true;
        for (String line : lines) {
            line = line.trim();
            if (first) {
                String[] parts = line.split(" ");
                if (parts.length < 2) {
                    log.e("malformed header line", line);
                    return;
                }
                prop.put("category", parts[0].trim());
                first = false;
            } else {
                if (header) {
                    if (!line.startsWith(fileType.blockHeader)) header = false;
                }
                if (header) {
                    String[] parts = line.substring(fileType.blockHeader.length()).split(":", 2);
                    if (parts.length == 2)
                        prop.setString(parts[0].trim().toLowerCase(), parts[1].trim());
                } else {
                    if (line.startsWith(fileType.blockLine))
                        line = line.substring(fileType.blockLine.length()).trim();
                    for (String s : fileType.blockIgnore) if (line.startsWith(s)) continue;
                    text.append(line).append('\n');
                }
            }
        }
        prop.setString(
                "file.ident",
                MFile.getFileNameOnly(file.getName())
                        + prop.getString("suffix", MCast.toString(cnt, 4)));
        content = null;
        text.append(removeQuots(textFooter));
        createManual(prop, placeholdersManual(prop, text.toString()));
    }

    private String placeholdersManual(MProperties prop, String text) {
        if (text.indexOf(placeholderBegin) < 0) return text;
        StringBuilder out = new StringBuilder();
        while (true) {
            int begin = text.indexOf(placeholderBegin);
            if (begin < 0) break;
            int end = text.indexOf(placeholderEnd, begin);
            if (end < 0) break;
            out.append(text.substring(0, begin));
            String key = text.substring(begin + placeholderBegin.length(), end);
            text = text.substring(end + placeholderEnd.length());
            String val = getValue(prop, key);
            if (val != null) out.append(val);
        }
        out.append(text);
        return out.toString();
    }

    private String getValue(MProperties prop, String key) {
        if (prop.containsKey(key)) return prop.getString(key, null);
        if (project.getProperties().containsKey(key))
            return project.getProperties().getProperty(key, null);
        if (key.equals("#date")) return new Date(timestamp).toString();
        if (key.equals("#isodate")) return MDate.toIsoDate(new Date(timestamp));
        if (key.equals("#hostname")) return MSystem.getHostname();
        log.w("key not found", key, prop);
        return null;
    }

    private void createManual(MProperties prop, String text) {

        String category = prop.getString("category", null);
        if (category == null) {
            log.w("Category not set", prop);
            return;
        }

        String fileName =
                MFile.normalize(prop.getString("file.ident", "xxx") + "." + outputExtension);
        File dir = new File(outputDirectory, MFile.normalize(category));
        dir.mkdirs();
        File file = new File(dir, fileName);
        log.i("create", file);
        MFile.writeFile(file, text);
        try {
            prop.save(new File(dir, fileName + ".properties"), false);
        } catch (IOException e) {
            log.e(file, e);
        }
    }

    private String removeQuots(String in) {
        if (in == null) return null;
        if (in.startsWith("\"") && in.endsWith("\"")) return in.substring(1, in.length() - 1);
        return in;
    }
}
