---
title: "Third Party Pipeline"
source: "https://tak.gov/user_builds"
author:
published:
created: 2026-05-16
description:
tags:
  - "clippings"
---
## Source Archive Requirements

Welcome to the TAK third party pipeline, an ephemeral build service which also signs third-party plugins, enabling operational baseline functionality. Information submitted though the third-party signing service is not monitored nor is it stored by the government. A visual indicator informs the end user that a plugin was signed using the third-party signing service rather than the official TAK Product Center build pipeline. The TAK Configuration Steering Board has open sourced ATAK-CIV and the Standard SDK on DoD GitHub and approved the public release of additional binaries on TAK.gov to support IRAD development. Access to these resources does not imply development support or operational transition.

For the build to succeed in this pipeline, the source code submission must meet the requirements that follow. For source code based upon a recent plugintemplate clone, the Gradle and Gradle plugin requirements have already been met.

- The source code must be provided in a zip archive, with a single root folder at its root. The root folder name will be used as the name for all APKs that are built by the service.
- Gradle must be the source code build system. Gradle scripts and associated folder must be part of the source archive, located at the root folder described below.
- The Gradle target, `assembleCivRelease`, must be defined.
- The TPC gradle plugin, `atak-gradle-takdev`, must be used for all references to the ATAK SDK. If there are libraries within the source archive that have ATAK SDK dependencies, those too must utilize the gradle plugin to resolve required artifacts. This plugin fetches the SDK from the TPC hosted maven repository, and the source will be compiled using these libraries.
	- For plugin versions targeting ATAK version 4.2 and beyond, atak-gradle-tak-dev must be the latest 2.x version available. A Maven range expression of 2.+ satisfies this requirement.
		- Plugins target prior to ATAK version 4.2 should use atak-gradle-takdev version must use the latest 1.x available. A Maven range expression of 1.+ satisfies this requirement.
- Plugins should be verified prior to submission with the USG developer's tak.gov credentials. For example, the command, `./gradlew -Ptakrepo.force=true -Ptakrepo.url=https://artifacts.tak.gov/artifactory/maven -Ptakrepo.user=<user> -Ptakrepo.password=<pass> assembleCivRelease` should build successfully where `<user>` and `<pass>` are the developer's artifacts.tak.gov credentials. If this command or an equivalent fails, then the build pipeline too will fail. When seeking TPC support, the output of the example command will be one of the first questions asked when supporting build issues. **NOTE:** access to artifacts.tak.gov is reserved for USG Federal and Military personnel at this time.
- The proguard-gradle entry `-repackageclasses atakplugin.PluginTemplate PluginTemplate` text should be replaced with a descriptor of your specific plugin. This helps with crash log identification.
- The AndroidManifest.xml must contain the below entry in order to be discoverable by ATAK:
`          <activity android:name="com.atakmap.app.component" tools:ignore="MissingClass">           <intent-filter android:label="@string/app_name">             <action android:name="com.atakmap.app.component" />           </intent-filter>         </activity>              `

## FAQ

Gradle version 6.9.1 and jdk 17 for our ATAK plugin baseline. The plugin template project, [https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV/tree/master/plugin-examples/plugintemplate](https://github.com/deptofdefense/AndroidTacticalAssaultKit-CIV/tree/master/plugin-examples/plugintemplate), is the primarily supported configuration.

The following versions of the **NDK** are installed:
- 12.1.2977051
- 21.0.6113669
- 21.4.7075529
- 23.0.7599858
- 25.1.8937393
  
The TPP does not allow for the installation of the NDK version declared in build.gradle at build time; the plugin **MUST** use one of the pre-installed versions.

The ATAK Development documentation ([https://tak.gov/documentation/resources/tak-developers/developer-documentation/atak-development](https://tak.gov/documentation/resources/tak-developers/developer-documentation/atak-development)) can be referenced for resources and changes to the build environment across ATAK versions.

**Submit your plugin by dragging and dropping files in the area below or use the button to select your files.**

You have no builds currently.