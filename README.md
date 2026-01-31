[![](https://jitpack.io/v/nbauma109/ecd.svg)](https://jitpack.io/#nbauma109/ecd)
[![](https://jitci.com/gh/nbauma109/ecd/svg)](https://jitci.com/gh/nbauma109/ecd)
[![CodeQL](https://github.com/nbauma109/ecd/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/nbauma109/ecd/actions/workflows/codeql-analysis.yml)
[![Update version](https://github.com/nbauma109/ecd/actions/workflows/update-version.yml/badge.svg)](https://github.com/nbauma109/ecd/actions/workflows/update-version.yml)
[![Github Release](https://github.com/nbauma109/ecd/actions/workflows/release.yml/badge.svg)](https://github.com/nbauma109/ecd/actions/workflows/release.yml)
[![Coverage Status](https://codecov.io/gh/nbauma109/ecd/branch/master/graph/badge.svg)](https://app.codecov.io/gh/nbauma109/ecd)

Downloads from Github releases :

[![Github Downloads (all releases)](https://img.shields.io/github/downloads/nbauma109/ecd/total.svg)]()
[![Github Downloads (latest release)](https://img.shields.io/github/downloads/nbauma109/ecd/latest/total.svg)]()

Downloads from Jitpack :

[![Jitpack Downloads](https://jitpack.io/v/nbauma109/ecd/month.svg)](https://jitpack.io/#nbauma109/ecd)
[![Jitpack Downloads](https://jitpack.io/v/nbauma109/ecd/week.svg)](https://jitpack.io/#nbauma109/ecd)

[![Download from Jitpack](https://github.com/nbauma109/ecd/assets/9403560/1bf8a2c6-b09a-442e-9888-5aa618f2558c)](https://jitpack.io/com/github/nbauma109/ecd/enhanced-class-decompiler/master-SNAPSHOT/enhanced-class-decompiler-master-SNAPSHOT.zip)

# ECD++ - Fork of Enhanced Class Decompiler ([ECD](https://github.com/ecd-plugin/ecd))
ECD++ integrates multiple decompilers provided by [transformer-api](https://github.com/nbauma109/transformer-api) :
- Fernflower
- Vineflower (fork of Fernflower)
- Procyon
- CFR
- JD-Core V0 and V1
- JADX

It also allows Java developers to **debug class files without source code directly**. It also integrates with the eclipse class editor, m2e plugin, supports **Javadoc**,  **reference search**, **library source attaching**, and the syntax of JDK8 **lambda** expression.

<img width="720" height="357" alt="image" src="https://github.com/user-attachments/assets/69c2bff3-7109-4286-9482-1a98dc4ae541" />

## Description
Enhanced Class Decompiler is a plug-in for the Eclipse platform. It integrates mutliple decompilers seamlessly with Eclipse, allows you to display all the Java sources during your debugging process, even if you do not have them all, and you can debug these class files without source code directly.

## Why a fork?
  * Compatibility with latest version of Eclipse over backward compatibility : upstream project aims at supporting all versions of Eclipse which is not maintainable from my point of view ([2b13e85](https://github.com/ecd-plugin/ecd/commit/2b13e856e0e557b155d658e5077c05efa8218e9c), [b518dd3](https://github.com/ecd-plugin/ecd/commit/b518dd30c4a045621f7a7e8c3b38ebedefb4cd09), [f2b6022](https://github.com/ecd-plugin/ecd/commit/f2b6022063dec1b4e15d9ddc7a31524240da1481), [8538745](https://github.com/ecd-plugin/ecd/commit/8538745e48485482bb8e0abc3fecc00f62527ca0), [8b47e95](https://github.com/ecd-plugin/ecd/commit/8b47e959916c337991c8ff4fe4637538df339565))
  * Usage of forked versions of decompilers : I need this freedom to bring bug fixes as some of the decompilers are not maintained anymore, or rarely maintained (see also this [Why a fork ?](https://github.com/nbauma109/procyon/tree/master?tab=readme-ov-file#why-a-fork-) section)
  * Source attach plugin comes with some enhancements (see below) but it was removed in upstream project ([#103](https://github.com/ecd-plugin/ecd/issues/103), [5a9a574](https://github.com/ecd-plugin/ecd/commit/5a9a5747159e43fe07c0f8b9ad2698bd3acad2fa))
  * Broken functions "Show Byte Code" and "Show Disassembler" are removed (bytecode is available natively in Eclipse in menu `Window -> Show View -> Bytecode`) but still present in upstream project
  * Some significant refactorings in upstream project made rebasing next to impossible without risk of regression ([e3d0e4e](https://github.com/ecd-plugin/ecd/commit/e3d0e4e29035807b6ce5bb46bf8607d9cd5162f6), [94a317a](https://github.com/ecd-plugin/ecd/commit/94a317aa4bcc9300befe3bc9a3ffd8924b1a9075), [6c3710c](https://github.com/ecd-plugin/ecd/commit/6c3710c4333d01bd45c70aa42e3c2fae15f0f250))

## Why is this plug-in "enhanced"?
This is an ad-free fork of the Eclipse Decompiler Plugin. So we enhanced it by removing all code which might compromise your privacy or security (to the best of our knowledge).

## How to install Enhanced Class Decompiler?

_If you have currently the "Eclipse" Class Decompiler installed, it is recommended to uninstall that plug-in first and remove the corresponding update site from your Eclipse installation._

Use one of the following options to install:

# Install with Eclipse Marketplace

In Eclipse, click on _"Help > Eclipse Marketplace..."_, search and install ecd++, or drag this button to the Marketplace popup.

[![Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client](https://marketplace.eclipse.org/modules/custom/eclipsefdn/eclipsefdn_marketplace/images/btn-install.svg)](/marketplace-client-intro?mpc_install=7323104"Drag to your running Eclipse* workspace. *Requires Eclipse Marketplace Client")

# Install with update site URL
  1. Launch _Eclipse_,
  2. Click on _"Help > Install New Software..."_,
  3. Use the update site URL https://nbauma109.github.io/ecd/updates/latest/
  4. Check the components to install,
  5. Click on "Next" and "Finish" buttons.
  6. A warning dialog windows appear because plug-in is not signed. Click on "Install anyway" button.

# Drag and Drop installation: 
  1. Launch _Eclipse_,
  2. Click on _"Help > Install New Software..."_,
  3. Drag and Drop enhanced-class-decompiler-x.y.z.zip
  4. Check the components to install,
  5. Click on "Next" and "Finish" buttons.
  6. A warning dialog windows appear because plug-in is not signed. Click on "Install anyway" button.

/!\ Drag and Drop is blocked in some corporate contexts. In this case, in _"Help > Install New Software..."_, click _"Add.."_ and then _"Archive.."_ to select enhanced-class-decompiler-x.y.z.zip

## How to check the file associations?
  1. Click on _"Window > Preferences > General > Editors > File Associations"_
  2. _"*.class"_ : _"Class Decompiler Viewer"_ is selected by default.
  3. _"*.class without source"_ : _"Class Decompiler Viewer"_ is selected by default.

## How to configure Enhanced Class Decompiler?
  1. Click on _"Window > Preferences > Java > Decompiler"_

Source attach:

You may configure a private Nexus repository with credentials (user/password) to download and attach sources automatically :

![image](https://github.com/user-attachments/assets/01dcaeec-59dd-40df-a837-53fcf25250ad)

You may select from a list of available public repositories :

![image](https://github.com/nbauma109/ecd/assets/9403560/27e345af-598d-4411-83d5-43617af14d1e)

You may use this option "Wait for sources to be downloaded before trying to decompile" to avoid decompiling code for which you have sources.

![image](https://github.com/nbauma109/ecd/assets/9403560/83530f27-4e17-47d8-afb5-0832cd842012)

Use this icon to switch decompiler while a class file is still open. Once a file is closed and re-opened, the source is cached, it is no more possible to switch decompiler.

![image](https://github.com/nbauma109/ecd/assets/9403560/3a8d92ad-41c0-453e-951f-92a2a9b67ac4)

## How to uninstall Enhanced Class Decompiler?
  1. Click on _"Help > About Eclipse > Installation Details > Installation Software"_,
  2. Select _"Enhanced Class Decompiler"_,
  3. Click on _"Uninstall..."_.

## How to build from source?

  Requirement: JDK 21 (make sure `JAVA_HOME` environment variable points to an appropriate JDK)

  If you want to test the latest features of this plugin, you have to build it from source. For this, proceed as following:

  1. `git clone https://github.com/nbauma109/ecd`
  2. build with ./build.sh on Linux or build.bat on Windows

  If you want to use Eclipse and help developing, continue like this:

  3. Install _Eclipse for RCP and RAP Developers_
  4. Import all projects into Eclipse by selecting _File_ > _Import_ > _General_ > _Existing Projects into Workspace_ > _Next_ and enter the parent of the cloned directory as "root directory".


## Licenses

The main plugin is licensed under [GPL 3](https://www.gnu.org/licenses/gpl-3.0-standalone.html), the other feature plugins is licensed under the [Eclipse Public License v1.0](https://www.eclipse.org/legal/epl-v10.html)

Code partially based on:
  * JD-Eclipse: Copyright Emmanuel Dupuy, [GPL 3](https://www.gnu.org/licenses/gpl-3.0-standalone.html)
  * Java Source Attacher: Copyright Thai Ha, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)

Used libraries:
  * Apache commons: Copyright (c) Apache Software Foundation, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Fernflower: Copyright (c) JetBrains, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
  * Minimal JSON: Copyright (c) 2013, 2014 EclipseSource, [MIT License](https://opensource.org/licenses/MIT)
  * CFR: Copyright Lee Benfield, [MIT License](https://opensource.org/licenses/MIT)
  * Procyon: Copyright Mike Strobel, [Apache License V2.0](https://www.apache.org/licenses/LICENSE-2.0.html)
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
* Nicolas Baumann (@nbauma109)
