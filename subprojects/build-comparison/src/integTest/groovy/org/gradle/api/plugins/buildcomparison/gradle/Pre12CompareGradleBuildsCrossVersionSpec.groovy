/*
 * Copyright 2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.plugins.buildcomparison.gradle

import org.gradle.api.plugins.buildcomparison.fixtures.BuildComparisonHtmlReportFixture
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.integtests.fixtures.executer.ExecutionResult
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.file.TestFile
import org.jsoup.Jsoup

@TargetVersions(["1.0", "1.1"])
class Pre12CompareGradleBuildsCrossVersionSpec extends CrossVersionIntegrationSpec {

    ExecutionResult result

    void applyPlugin(TestFile file = buildFile) {
        versionGuard(file) { "apply plugin: 'compare-gradle-builds'" }
    }

    def "can compare identical builds with source pre 1.2"() {
        given:
        applyPlugin()
        buildFile << "apply plugin: 'java'"
        versionGuard { """
            compareGradleBuilds {
                sourceBuild.gradleVersion "${previous.version.version}"
            }
        """ }

        and:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        runComparisonWithCurrent()
        sourceWasInferred()

        then:
        def report = report();
        report.sourceBuildVersion == previous.version.version
        report.targetBuildVersion == current.version.version
    }

    def "can compare identical builds with target pre 1.2"() {
        given:
        applyPlugin()
        buildFile << "apply plugin: 'java'"
        versionGuard { """
            compareGradleBuilds {
                targetBuild.gradleVersion "${previous.version.version}"
            }
        """ }

        and:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        runComparisonWithCurrent()
        targetWasInferred()

        then:
        def report = report();
        report.sourceBuildVersion == current.version.version
        report.targetBuildVersion == previous.version.version
    }

    def "can compare different builds with source pre 1.2"() {
        given:
        applyPlugin()
        buildFile << "apply plugin: 'java'"
        versionGuard { """
            compareGradleBuilds {
                sourceBuild.gradleVersion "${previous.version.version}"
            }

            compileJava { options.debug = !options.debug }
        """ }

        and:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        failBecauseNotIdentical()
        sourceWasInferred()

        then:
        def report = report();
        report.sourceBuildVersion == previous.version.version
        report.targetBuildVersion == current.version.version
    }

    def "can compare different builds with target pre 1.2"() {
        given:
        applyPlugin()
        buildFile << "apply plugin: 'java'"
        versionGuard { """
            compareGradleBuilds {
                targetBuild.gradleVersion "${previous.version.version}"
            }

            compileJava { options.debug = !options.debug }
        """ }

        and:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        failBecauseNotIdentical()
        targetWasInferred()

        then:
        def report = report();
        report.sourceBuildVersion == current.version.version
        report.targetBuildVersion == previous.version.version
    }

    protected versionGuard(TestFile file = buildFile, Closure string) {
        file << "\nif (GradleVersion.current().version == '${current.version.version}') {\n"
        file << string()
        file << "\n}\n"
    }

    protected ExecutionResult runComparisonWithCurrent() {
        result = currentExecuter().run()
        result
    }

    protected GradleExecuter currentExecuter() {
        current.executer(temporaryFolder).requireGradleDistribution().withStackTraceChecksDisabled().withTasks("compareGradleBuilds")
    }

    BuildComparisonHtmlReportFixture report(path = "build/reports/compareGradleBuilds/index.html") {
        new BuildComparisonHtmlReportFixture(Jsoup.parse(file(path), null))
    }

    void failBecauseNotIdentical() {
        result = currentExecuter().runWithFailure()
        result.assertHasCause("The build outcomes were not found to be identical. See the report at: file:///")
    }

    void sourceWasInferred(def html = this.report()) {
        html.sourceWasInferred()
        hasInferredLogWarning("source")
    }

    void targetWasInferred(def html = this.report()) {
        html.targetWasInferred()
        hasInferredLogWarning("target")
    }

    private void hasInferredLogWarning(String buildName) {
        assert result.output.contains("The build outcomes for the $buildName build will be inferred from the")
    }


}
