package de.mhus.mvn.manual;

import java.io.File;
import java.io.IOException;
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
        aggregator = true
		)
public class CollectorMojo extends AbstractMojo {

	private Log log = new MavenPluginLog(this);
	private long timestamp;
	
	@Parameter
	public String[] extensions = new String[] {"java"};
	
	@Parameter
	public String[] start = new String[] {"src/main/java"};
	
	@Parameter
	public String[] exclude = new String[] {"bin","target","test"};
	
	@Parameter(defaultValue = "${project}")
    protected MavenProject project;

	@Parameter
	public String placeholderBegin = "{{";
	
	@Parameter
	public String placeholderEnd = "}}";
	
	@Parameter(required=true)
	public String outputDirectory;
	
	@Parameter
	public String outputExtension = "adoc";
	
	@Parameter
	public boolean cleanupOutputDirectory = false;
	
	@Parameter
	public boolean generateIndexFiles = false;
	
	@Parameter
	public String indexFileName = "index.adoc";
	
	@Parameter
	public String indexHeader = "";
	
	@Parameter
	public String indexFooter = "";
	
	@Parameter
	public String indexLine = "include::{{_file}}[]";

	@Parameter
	public String textHeader = "";
	
	@Parameter
	public String textFooter = "";
	
	@Parameter
	public String rootDirectory = ".";
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		timestamp = System.currentTimeMillis();
		File root = new File(rootDirectory);
		
		
		if (cleanupOutputDirectory)
			deleteOutputDirectory();
		
		findStart(root);
		
		if (generateIndexFiles)
			generateIndexFiles();
	}

	private void generateIndexFiles() {
		for (File dir : new File(outputDirectory).listFiles()) {
			if (dir.isDirectory() && !dir.getName().startsWith(".")) {
				generateIndexFile(dir);
			}
		}
	}

	private void generateIndexFile(File dir) {
		TreeMap<String,MProperties> list = new TreeMap<>();
		for (File file : dir.listFiles()) {
			if (file.isFile() && file.getName().endsWith("."+outputExtension)) {
				MProperties prop = MProperties.load(new File(file.getPath() + ".properties"));
				prop.setString("_file", file.getName());
				list.put(prop.getString("sort", "") + "_" + file.getName(), prop);
			}
		}
		StringBuilder out = new StringBuilder().append(removeQuots(indexHeader)).append("\n");
		for (Entry<String, MProperties> entry : list.entrySet()) {
			String line = placeholdersManual(entry.getValue(), removeQuots(indexLine));
			out.append(line).append("\n");
		}
		out.append(removeQuots(indexFooter));
		File indexFile = new File(dir,indexFileName);
		MFile.writeFile(indexFile, out.toString());
	}

	private void deleteOutputDirectory() {
		File dir = new File(outputDirectory);
		MFile.deleteDir(dir);
		dir.mkdirs();
	}

	private void findStart(File dir) {
		log.d("findStart",dir);
		if (MCollection.contains(exclude, dir.getName())) {
			log.d("ignore",dir);
			return;
		}
		String dirPath = dir.getPath().replace('\\', '/');
		for (String s : start) {
			if (dirPath.endsWith(s)) {
				log.d("start",dir);
				parseDir(dir, dir);
				return;
			}
		}
		for (File d : dir.listFiles()) {
			if (d.isDirectory() && !d.getName().startsWith("."))
				findStart(d);
		}
	}

	private void parseDir(File dir, File start) {
		log.d("parseDir",dir);
		for (File d : dir.listFiles()) {
			if (d.isDirectory() && !d.getName().startsWith("."))
				parseDir(d, start);
			if (d.isFile() && !d.getName().startsWith(".")) {
				for (String ext : extensions) {
					if (d.getName().endsWith("."+ext)) {
						parseFile(d, start);
						continue;
					}
				}
			}
		}
	}

	private void parseFile(File file, File start) {
		log.d("parseFile",file);
		String content = MFile.readFile(file);
		while (true) {
			int begin = content.indexOf("/*#");
			if (begin < 0) return;
			int end = content.indexOf("*/", begin+3);
			if (end < 0) {
				log.w("start without end token");
				return;
			}
			String part = content.substring(begin+1, end);
			content = content.substring(end+2);
			parseManual(part, file, start);
		}
		
	}

	private void parseManual(String content, File file, File start) {
		log.d("parseManual",content);
		String[] lines = content.split("\n");
		MProperties prop = new MProperties();
		prop.setString("file.name", file.getName());
		prop.setString("file.ident", MFile.getFileNameOnly(file.getName()));
		prop.setString("file.path", file.getAbsolutePath().substring(start.getAbsolutePath().length()));
		prop.setString("file.start", start.getPath());
		StringBuilder text = new StringBuilder().append(removeQuots(textHeader));
		boolean header = true;
		for (String line : lines) {
			line = line.trim();
			if (header) {
				if (!line.startsWith("*#"))
					header = false;
			}
			if (header) {
				String[] parts = line.substring(2).split(":",2);
				if (parts.length == 2)
					prop.setString(parts[0].trim().toLowerCase(), parts[1].trim());
			} else {
				if (line.startsWith("*"))
					line = line.substring(1).trim();
				text.append(line).append('\n');
			}
		}
		content = null;
		text.append(removeQuots(textFooter));
		saveManual(prop, placeholdersManual(prop, text.toString()));
		
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
			String key = text.substring(begin+placeholderBegin.length(), end);
			text = text.substring(end+placeholderEnd.length());
			String val = getValue(prop, key);
			if (val != null)
				out.append(val);
		}
		out.append(text);
		return out.toString();
	}

	private String getValue(MProperties prop, String key) {
		if (prop.containsKey(key))
			return prop.getString(key, null);
		if (project.getProperties().containsKey(key))
			return project.getProperties().getProperty(key, null);
		if (key.equals("#date"))
			return  new Date(timestamp).toString();
		if (key.equals("#isodate"))
			return MDate.toIsoDate(new Date(timestamp));
		if (key.equals("#hostname"))
			return MSystem.getHostname();
		log.w("key not found",key,prop);
		return null;
	}

	private void saveManual(MProperties prop, String text) {
		
		String category = prop.getString("category", null);
		if (category == null) {
			log.w("Category not set",prop);
			return;
		}
		
		String fileName = MFile.normalize(prop.getString("file.ident", "xxx") + "." + outputExtension);
		File dir = new File(outputDirectory, MFile.normalize(category));
		dir.mkdirs();
		File file = new File(dir,fileName);
		log.i("saveManual",file);
		MFile.writeFile(file, text);
		try {
			prop.save(new File(dir, fileName + ".properties"));
		} catch (IOException e) {
			log.e(file,e);
		}
	}

	private String removeQuots(String in) {
		if (in == null) return null;
		if (in.startsWith("\"") && in.endsWith("\""))
			return in.substring(1, in.length()-1);
		return in;
	}
}
