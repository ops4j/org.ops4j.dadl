/*
 * Copyright 2015 OPS4J Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ops4j.dadl.maven;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/** 
 * Generates sources from a DADL model.
 * 
 * @author hwellmann
 *
 */
@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_SOURCES)
public class GenerateMojo extends AbstractDadlMojo {

    @Parameter(defaultValue = "${project.build.directory}/generated-sources/dadl")
    private File outputDir;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        String outputRoot = getOutputDir().getAbsolutePath();
        getLog().info("Generating additional source directory " + outputRoot);
        project.addCompileSourceRoot(outputRoot);
        generateJavaSources();
    }
    
    @Override
    public File getOutputDir() {
        return outputDir;
    }
}
