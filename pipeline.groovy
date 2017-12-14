node {
   def mvnHome
   stage('Build') { // for display purposes
      // Get some code from a GitHub repository
      git 'https://github.com/maodage2005/helloworld.git'
      
      sh "echo ${BUILD_NUMBER} > ./src/main/webapp/version.txt"
      // Get the Maven tool.
      // ** NOTE: This 'M3' Maven tool must be configured
      // **       in the global configuration.           
      mvnHome = tool 'maven_3.5.2'
      sh "'${mvnHome}/bin/mvn' -Dmaven.test.failure.ignore clean package"
      
      //copy the package to share folder
      sh "cp ${WORKSPACE}/target/helloworld.war /data/packages/helloworld.war -f" 
      sh "cp ${WORKSPACE}/target/helloworld.war /data/packages/helloworld-${BUILD_NUMBER}.war" 
   }
   stage('SonarQube analysis') {
    dir('./ansible'){
            //sh "cp /data/packages/helloworld-${BUILD_NUMBER}.war ./playbook/helloworld.war -f"
            sh "chmod 400 ./files/HudsonKeyPair.pem"
            sh "/usr/local/bin/ansible-playbook ./playbook/startSonar.yml -u ec2-user"
            sh "chmod 644 ./files/HudsonKeyPair.pem"
        }   
    sleep(10)
    def scannerHome = tool 'sonar_scan';
    withSonarQubeEnv('sonar') {
      //sh "'${mvnHome}/bin/mvn' sonar:sonar"
      sh "${scannerHome}/bin/sonar-scanner -e -Dsonar.host.url=http://10.255.77.128:8111/ -Dproject.settings=./pom.xml -Dsonar.projectName=helloworld -Dsonar.projectVersion=1.0 -Dsonar.projectKey=hudson.test.helloworld -Dsonar.sources=./src -Dsonar.projectBaseDir=."
    } // SonarQube taskId is automatically attached to the pipeline context
    
    stage('DEV Env'){
        //create the dev env
        dir('./docker'){
         sh "cp /data/packages/helloworld.war . -f"
         sh "docker build --rm --tag testweb:latest ."
         sh "docker rm -f devenv || true"
         sh "docker run -d --rm --name devenv  -p 8111:8080 testweb:latest"
        }
        sleep(5)//wait for the tomcat startup
        //smoke test the dev env
        version = sh(
            script:"curl http://localhost:8111/helloworld/version.txt",
            returnStdout: true
        ).trim()
        if(version == "${BUILD_NUMBER}"){
            return true
        }
        else{
            return false
        }
    }
    
    stage('QA Env'){
        //confirm first
        input 'Do you approve deployment?'
            
        dir('./ansible'){
            sh "cp /data/packages/helloworld-${BUILD_NUMBER}.war ./playbook/helloworld.war -f"
            sh "chmod 400 ./files/HudsonKeyPair.pem"
            sh "/usr/local/bin/ansible-playbook ./playbook/deploy2Tomcat.yml -u ec2-user --extra-vars 'version=${BUILD_NUMBER}'"
            sh "chmod 644 ./files/HudsonKeyPair.pem"
        }
    }
    
     stage('Prod Env'){
        input 'Do you approve deployment?'
		
		dir('./ansible'){
            //sh "cp /data/packages/helloworld-${BUILD_NUMBER}.war ./playbook/helloworld.war -f"
            sh "chmod 400 ./files/HudsonKeyPair.pem"
            sh "/usr/local/bin/ansible-playbook ./playbook/deploy2Tomcat.yml -u ec2-user -i ./inventories/production_hosts --extra-vars 'version=${BUILD_NUMBER}'"
            sh "chmod 644 ./files/HudsonKeyPair.pem"
        }
     }
    
  }
   
   
}