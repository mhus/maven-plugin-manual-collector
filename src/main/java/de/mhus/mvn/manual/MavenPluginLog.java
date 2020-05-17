package de.mhus.mvn.manual;

import org.apache.maven.plugin.AbstractMojo;

import de.mhus.lib.core.logging.Log;

public class MavenPluginLog extends Log {

	public MavenPluginLog(AbstractMojo owner) {
		super(owner);
		engine = new MavenPluginLogEngine(owner);
	}

}
