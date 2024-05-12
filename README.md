[![](https://jitpack.io/v/nbauma109/ecd.svg)](https://jitpack.io/#nbauma109/ecd)
[![](https://jitci.com/gh/nbauma109/ecd/svg)](https://jitci.com/gh/nbauma109/ecd)
[![CodeQL](https://github.com/nbauma109/ecd/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/nbauma109/ecd/actions/workflows/codeql-analysis.yml)
[![Update version](https://github.com/nbauma109/ecd/actions/workflows/update-version.yml/badge.svg)](https://github.com/nbauma109/ecd/actions/workflows/update-version.yml)
[![Github Release](https://github.com/nbauma109/ecd/actions/workflows/release.yml/badge.svg)](https://github.com/nbauma109/ecd/actions/workflows/release.yml)

Downloads from Github releases :

[![Github Downloads (all releases)](https://img.shields.io/github/downloads/nbauma109/ecd/total.svg)]()
[![Github Downloads (latest release)](https://img.shields.io/github/downloads/nbauma109/ecd/latest/total.svg)]()

Downloads from Jitpack :

[![Jitpack Downloads](https://jitpack.io/v/nbauma109/ecd/month.svg)](https://jitpack.io/#nbauma109/ecd)
[![Jitpack Downloads](https://jitpack.io/v/nbauma109/ecd/week.svg)](https://jitpack.io/#nbauma109/ecd)

[![Download from Jitpack](https://github.com/nbauma109/ecd/assets/9403560/1bf8a2c6-b09a-442e-9888-5aa618f2558c)](https://jitpack.io/com/github/nbauma109/ecd/enhanced-class-decompiler/master-SNAPSHOT/enhanced-class-decompiler-master-SNAPSHOT.zip)

# Enhanced Class Decompiler
Enhanced Class Decompiler integrates **JD**, **FernFlower**, **Vineflower**, **CFR**, **Procyon** seamlessly with Eclipse and allows Java developers to **debug class files without source code directly**. It also integrates with the eclipse class editor, m2e plugin, supports **Javadoc**,  **reference search**, **library source attaching**, **byte code view** and the syntax of JDK8 **lambda** expression.

<p align="center"><img src="https://ecd-plugin.github.io/ecd/doc/o_debug_class.png"></p>

## Description
Enhanced Class Decompiler is a plug-in for the Eclipse platform. It integrates JD, FernFlower, Vineflower, CFR, Procyon seamlessly with Eclipse, allows you to display all the Java sources during your debugging process, even if you do not have them all, and you can debug these class files without source code directly.

## Why is this plug-in "enhanced"?
This is an ad-free fork of the Eclipse Decompiler Plugin. So we enhanced it by removing all code which might compromise your privacy or security (to the best of our knowledge).

## How to install Enhanced Class Decompiler?

Drag and Drop installation: 

_If you have currently the "Eclipse" Class Decompiler installed, it is recommended to uninstall that plug-in first and remove the corresponding update site from your Eclipse installation._
  1. Launch _Eclipse_,
  2. Click on _"Help > Install New Software..."_,
  3. Drag and Drop enhanced-class-decompiler-x.y.z.zip
  4. Check the components to install,
  5. Click on "Next" and "Finish" buttons.
  6. A warning dialog windows appear because plug-in is not signed. Click on "Install anyway" button.

## How to check the file associations?
  1. Click on _"Window > Preferences > General > Editors > File Associations"_
  2. _"*.class"_ : _"Class Decompiler Viewer"_ is selected by default.
  3. _"*.class without source"_ : _"Class Decompiler Viewer"_ is selected by default.

## How to configure Enhanced Class Decompiler?
  1. Click on _"Window > Preferences > Java > Decompiler"_

## How to uninstall Enhanced Class Decompiler?
  1. Click on _"Help > About Eclipse > Installation Details > Installation Software"_,
  2. Select _"Enhanced Class Decompiler"_,
  3. Click on _"Uninstall..."_.

## How to build from source?

  Requiremnent: JDK 11 or newer (make sure `JAVA_HOME` environment variable points to an appropriate JDK)

  If you want to test the latest features of this plugin, you have to build it from source. For this, proceed as following:

  1. `git clone https://github.com/ecd-plugin/ecd`
  2. `git clone --depth 1 https://github.com/ecd-plugin/update` next to this project
  3. Run `mvn clean package`

  If you want to use Eclipse and help developing, continue like this:

  4. Install _Eclipse for RCP and RAP Developers_
  3. Import all projects into Eclipse by selecting _File_ > _Import_ > _General_ > _Existing Projects into Workspace_ > _Next_ and enter the parent of the cloned directory as "root directory".
  4. Open the _org.sf.feeling.decompiler.updatesite_ project in the Package Explorer
  5. Open the file _site.xml_ within the project
  6. Press "Build All"
  7. Copy the jar files generated in the _build/features_ and _build/plugins_ folder of the project into the correspondent folders of your normal Eclipse installation.

## Plugin Signature

Since version 3.3.0 ECD is signed by a self-signed 4096 bit RSA key:

* Subject: `CN=ECD Software Distribution,OU=ECD,O=ECD`
* SHA-1 fingerprint: 2D DB EE 7E 07 32 EB 0D 7C F2 FF C6 68 A0 C4 B8 B9 58 40 29
* SHA-256 fingerprint: 8A 68 55 D3 91 B7 6F 95 DA D1 1E DF 1C 38 8D 38 F1 8A 0C A2 97 E5 12 85 DD 5B 05 9C C3 21 1B D4
* Certificate file: [ecd.cer](ecd.cer)

## Licenses

The main plugin and the _org.sf.feeling.decompiler.jd_ project are licensed under [GPL 3](https://www.gnu.org/licenses/gpl-3.0-standalone.html), the other feature plugins are licensed under the [Eclipse Public License v1.0](https://www.eclipse.org/legal/epl-v10.html)

Code partially based on:
  * JD-Eclipse: Copyright Emmanuel Dupuy, [GPL 3](https://www.gnu.org/licenses/gpl-3.0-standalone.html)
  * Java Source Attacher: Copyright Thai Ha, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)

Used libraries:
  * Dr. Garbage Tools: Copyright (c) Dr. Garbage Ltd. & Co KG, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Apache commons: Copyright (c) Apache Software Foundation, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Fernflower: Copyright (c) JetBrains, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Minimal JSON: Copyright (c) 2013, 2014 EclipseSource, [MIT License](https://opensource.org/licenses/MIT)
  * CFR: Copyright Lee Benfield, [MIT License](https://opensource.org/licenses/MIT)
  * Procyon: Copyright Mike Strobel, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Netbeans CVSClient: Copyright (c) NetBeans Community, [Eclipse Public License v1.0](https://www.eclipse.org/legal/epl-v10.html) and [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Maven SCM: Copyright (c) Apache Software Foundation, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Nexus Indexer: [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Nexus Restlet1x Model: [Eclipse Public License v1.0](https://www.eclipse.org/legal/epl-v10.html)
  * Plexus Utils: Copyright The Codehaus Foundation, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * JD-Core: Copyright Emmanuel Dupuy, [GPL 3](https://www.gnu.org/licenses/gpl-3.0-standalone.html)
  * Vineflower: [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)

## Contributors

* Chen Chao (cnfree2000@hotmail.com) - initial API and implementation
* Robert Zenz
* Pascal Bihler
* Nick Lombard
* Jan Peter Stotz
