# Creating an unsigned release

    mvn clean package 

This command will create the release repository ZIP file in `../update/target/com.github.ecd-plugin.update-<version>.zip`

# Creating a release with signed JAR files

(For ECD maintainers):

    mvn clean package -Dsigning.disabled=false -Dsigning.password=<password> -Dsigning.keystore=<absolute path-to-keystore>

This command will create the release repository ZIP file in `../update/target/com.github.ecd-plugin.update-<version>.zip`