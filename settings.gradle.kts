val codeartifactUrl = "https://homelab-eu-north-1-prod-dependencies-352124045884.d.codeartifact.eu-north-1.amazonaws.com/maven/maven/"
val codeartifactUsername = "aws"
val codeartifactTokenVariable = "CODEARTIFACT_AUTH_TOKEN"
val publicSourceName = "public"

val dependencyMirrorDir = providers.gradleProperty("dependencyMirrorDir")
val dependencySource = providers.gradleProperty("dependencySource")
val codeartifactToken = providers.environmentVariable(codeartifactTokenVariable).orElse("")

val usesMirror = dependencyMirrorDir.isPresent
if (usesMirror && !File(dependencyMirrorDir.get()).isAbsolute) {
    throw GradleException("dependencyMirrorDir must be an absolute path, got '${dependencyMirrorDir.get()}'")
}
val usesPublicSource = when (val source = dependencySource.orNull) {
    null -> false
    publicSourceName -> if (usesMirror) throw GradleException("dependencySource '$publicSourceName' and dependencyMirrorDir are both set, set only one") else true
    else -> throw GradleException("dependencySource must be '$publicSourceName' or unset, got '$source'")
}

fun RepositoryHandler.addLibrarySource() {
    when {
        usesMirror -> maven {
            name = "dependencyMirror"
            url = File(dependencyMirrorDir.get()).toURI()
            metadataSources {
                gradleMetadata()
                mavenPom()
            }
        }
        usesPublicSource -> mavenCentral()
        else -> maven {
            if (codeartifactToken.get().isEmpty()) {
                logger.warn("$codeartifactTokenVariable is empty: set it to a CodeArtifact authorization token")
            }
            name = "codeartifact"
            url = uri(codeartifactUrl)
            credentials {
                username = codeartifactUsername
                password = codeartifactToken.get()
            }
        }
    }
}

fun RepositoryHandler.addPluginSource() {
    addLibrarySource()
    if (usesPublicSource) {
        gradlePluginPortal()
    }
}

pluginManagement.repositories.addPluginSource()

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories.addLibrarySource()
}

rootProject.name = "betterreads-backend"
