package org.shipkit.auto.version;

import com.github.zafarkhaja.semver.Version;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

import java.io.File;
import java.util.Collection;
import java.util.Optional;

import static org.shipkit.auto.version.NextVersionPicker.explainVersion;

/**
 * Main functionality, should never depend on any of Gradle APIs.
 */
class AutoVersion {

    private final static Logger LOG = Logging.getLogger(AutoVersion.class);

    private final ProcessRunner runner;
    private final File versionFile;

    AutoVersion(ProcessRunner runner, File versionFile) {
        this.runner = runner;
        this.versionFile = versionFile;
    }

    AutoVersion(File projectDir) {
        this(new ProcessRunner(projectDir), new File(projectDir, "version.properties"));
    }

    /**
     * Deduct version based on existing tags (will run 'git tag'), and the version spec from versionFile field.
     *
     * @param projectVersion the version of the gradle project before running the plugin
     */
    DeductedVersion deductVersion(String projectVersion) {
        return deductVersion(LOG, projectVersion);
    }

    //Exposed for testing so that 'log' can be mocked
    DeductedVersion deductVersion(Logger log, String projectVersion) {
        Optional<Version> previousVersion = Optional.empty();
        VersionConfig config = VersionConfig.parseVersionFile(versionFile);

        try {
            Collection<Version> versions = new VersionsProvider(runner).getAllVersions(config.getTagPrefix());
            PreviousVersionFinder previousVersionFinder = new PreviousVersionFinder();

            if (config.getRequestedVersion().isPresent()) {
                previousVersion = previousVersionFinder.findPreviousVersion(versions, config);
            }

            String nextVersion = new NextVersionPicker(runner, log).pickNextVersion(previousVersion,
                    config, projectVersion);

            if (!config.getRequestedVersion().isPresent()) {
                previousVersion = previousVersionFinder.findPreviousVersion(versions, new VersionConfig(nextVersion, config.getTagPrefix()));
            }

            logPreviousVersion(log, previousVersion);

            return new DeductedVersion(nextVersion, previousVersion, config.getTagPrefix());
        } catch (Exception e) {
            String message = "caught an exception, falling back to reasonable default";
            log.debug("shipkit-auto-version " + message, e);
            String v = config.getRequestedVersion().orElse("0.0.1-SNAPSHOT").replace("*", "unspecified");
            explainVersion(log, v, message + "\n  - run with --debug for more info");
            return new DeductedVersion(v, previousVersion, config.getTagPrefix());
        }
    }

    private void logPreviousVersion(Logger log, Optional<Version> previousVersion) {
        log.info("[shipkit-auto-version] " + previousVersion
                .map(version -> "Previous version: " + version)
                .orElse("No previous version"));
    }
}
