pipeline {
   agent { label any }
    environment {
	            PATH="/usr/jenkins/sonar-scanner-4.7.0.2747-linux/bin:${env.PATH}"
	            HTDOCS_PATH = "/usr/IBM/HTTPServer/htdocs/"
	            DIST_PATH = "/desktop/dist"
	            NODE_MOD_PATH = "/usr/jenkins/"
	            JENKINSURL = "http://test-jenkins.devops/view/DevOps/job/challenge/job/tmdb-devops-hallange/"
	            SonarURL = "http://10.242.212.177:9000"
	            def devTeamEmails = "test@gmail.com"
                def repo="tmdb-devops-challenge"
				def PACKAGE_NAME=""tmdb-devops-challenge"
				def arbArtifactoryLink="https://artifactory.devops/artifactory/challenge"
				def byUser="${currentBuild.buildCauses.shortDescription}"
				def emailLink = "NON"
				def emailDev = "NON"
				def build_ok = false
				def lastSuccessfulBuildID = 0
				def transitionInput ="[transition:[id:'141']]"
				def buildInfo='rootDir:"Successfully deployed",buildFile:"File",tasks:"Deploy"'
				def  jiracomment = "$JiraStep"
								}
    parameters{
        choice(name: 'environment', choices: ['prtsi1a-challenge'], description: '')
    }
    stages {
       stage ('Git Branches') {
			agent { any }
			steps {
				withCredentials([usernamePassword(credentialsId: 'devops', usernameVariable: 'username', passwordVariable: 'password')]){
        		checkout([$class: 'GitSCM', branches: [[name: "${params.branch}"]], doGenerateSubmoduleConfigurations: false, extensions: [], gitTool: 'Default', submoduleCfg: [],
        		userRemoteConfigs: [[url: "https://github.com/veeragiri/tmdb-devops-challenge.git"]]])}
			}
        }
        stage ('Initialize') {
			agent { any }
            steps {
                echo "Branch     : ${branch}"
                echo "Environment : ${environment}"
            }
		}
        stage ('Cloning Code') {
            agent { any }
            steps {
                script {
                     def gitbranch="${branch}".split("/")[1]
                    echo "branch selected:"+gitbranch
                    sh "rm -rf *"
					
					sh "git clone -b '${gitbranch}' https://github.com/veeragiri/tmdb-devops-challenge.git"
					}                    }
                }
            }
        }
        stage('Sonar-scanner'){
            agent { any }
            steps {
                
                dir("tmdb-devops-challenge"){
                withSonarQubeEnv('SonarQube') {
                sh 'sonar-scanner -Dsonar.projectKey=tmdb-devops-challenge -Dsonar.sources=. -Dsonar.host.url=http://10.242.212.177:9000 -Dsonar.login=dbabb2e3753137163bdbd48682e67b563dc59094 -Dsonar.projectVersion=$BUILD_NUMBER'
                    }
                }
            }
        }
         stage('Sonar-qualitygates'){
            agent { any }
            steps {
                
                 dir("tmdb-devops-challenge"){
                    script {
                    def sonarqubeurl = "http://10.242.212.177:9000"
                    def sonarqubeToken = "dbabb2e3753137163bdbd48682e67b563dc59094"
                    def projectKey = "tmdb-devops-challenge"
                    def qualityGateStatusUrl = "${sonarqubeurl}/api/qualitygates/project_status?projectKey=${projectKey}"
                    def sonarqubeQualityGateStatus = sh(script: "curl -k -u ${sonarqubeToken}: '${qualityGateStatusUrl}'", returnStdout: true).trim()
                if (sonarqubeQualityGateStatus.contains('"status":"ERROR"')) {
                        currentBuild.result = 'FAILURE'
                        error "Sonarqube quality gate failed. See Sonarqube for details."
                    } else {
                        currentBuild.result = 'SUCCESS'
                        echo "Sonarqube quality gate passed. Proceeding with the build."
                    }
                }
                }
               
                        echo "Sonarqube quality gate passed. Proceeding with the build."
            } 
        } 
        
       
        
        stage('Build') {
            agent { any }
            steps {
                dir("tmdb-devops-challenge"){
                sh "echo $JAVA_HOME"
                sh "unzip /usr/jenkins/node_modules.zip"
    			sh "npm run sit"
                }
                sh '''
					cd $WORKSPACE/${DIST_PATH}
					tar -cvzf tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz tmdb-devops-challenge
			    '''
            }
        }
        
          stage('Unit Test'){
            agent { any }
           
           
            steps {
                echo "running unit-tests"
                
             dir("tmdb-devops-challenge"){
                      sh "echo $JAVA_HOME"
                 		sh "npm run test"
                }
           
              
            }
        }
        
        stage('Upload Portal to artifactory') {
            agent { any }
            steps {
                  dir("tmdb-devops-challenge/dist"){
                		archiveArtifacts  artifacts:'tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz', fingerprint: true
                  }
                script{
                
		
                def arbArtifactoryHttpLink = arbArtifactoryLink.replace("https","http")
                echo '############Upload to artifactory########'
                    sh "curl -X PUT -u jenkins-user:jenkins-user -T $WORKSPACE/tmdb-devops-challenge/dist/tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz  '${arbArtifactoryHttpLink}/tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz' "
				   	
				}
            }
          }
          stage('Removing Build files') {
              agent { any }
              steps {
                  script {
                    lastSuccessfulBuildID =  currentBuild.previousSuccessfulBuild?.id
                    echo "RM-2 The last successful build number is : ${lastSuccessfulBuildID}"
                    sh '''
					cd $WORKSPACE/${DIST_PATH}
					rm -f tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz
			    '''  
                  }
              }
              
          }
        stage('Download on Target Env') {
            agent { label "${environment}" }
            steps {
                script {
                copyArtifacts   projectName: env.JOB_NAME, selector: [$class: 'SpecificBuildSelector', buildNumber: env.BUILD_NUMBER], filter: 'retail-desktop-revamp-1.0.4-retail-dt-PC-SNAPSHOT.tgz', fingerprintArtifacts: true
                echo 'Download Portal'
				sh 'mv tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz ${HTDOCS_PATH}'
                }
            }
        }
        stage('Deploy Portal') {
            agent { label "${environment}" }
                steps {
               
              
                    catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                    dir("${HTDOCS_PATH}"){
                    script{
                        
                        def now = new Date()
                        def nowFormat =  now.format("yyyyMMddHHmm")
		                sh """
	                    	gzip -fd tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz
		                    tar -xvf tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tar
		                    mv tmdb-devops-challenge tmdb-devops-challenge_$nowFormat
		                    mv tmdb-devops-challenge devops-challenge
		                    chmod -R 755 devops-challenge
		                    """
		                    build_ok = "true" // Here indicate whether deploy stage final status
		                    echo 'RM-1 deployment Done'
		                    
		                    // if env is SIT  Jira comment will update
                        if ( "${environment}" == 'NewChallenge') {
						jiracomment =  "SitDeployment"
						} else if( "${environment}" == 'NewChallengeSIT'){
						jiracomment =  "SITTesting"
						}else if( "${environment}" == 'NewChallengeUAT' ){
						jiracomment =  "UATDeployment"
						}else if( "${environment}" == 'NewChallengeUAT1' ){
						jiracomment =  "UATTesting"
						}
						
						if("${JiraStep}"=="Submit"){
						    	jiracomment =  "SitDeployment"
							JiraStep=  "SitDeployment"
						}
       
                    }


                        }
                    
		          }
		         
	       
                    
                } 
	        
		  
		  post {  
        always{
                script {
					if ( "${environment}" == 'NewChallengeSIT') {
						emailLink="https://sit.codechallenge.com/portalnew/"
						emailEnv="SIT1"
						echo 'RM-2 SIT1 deployment Done'
						} else if( "${environment}" == 'NewChallengeUAT'){
						emailLink="https://sit.codechallenge.com/business2/"
						emailEnv="SIT2"
						echo 'RM-2 SIT2 deployment Done'
						}else if( "${environment}" == 'NewChallengeUAT1' ){
						emailLink="https://uat.codechallenge.com./business2/"
						emailEnv="UAT1"
					        echo 'RM-2 UAT1 deployment Done'
						}
						}
        }
        
            success{
					emailext body: "The Build number: ($BUILD_NUMBER) for Code Challenge pipeline: ($JOB_NAME) has done successfully.\n URL: $JENKINSURL \r\nLink: $emailLink\r\nEnvironment: $emailEnv \n Sonar URL: $SonarURL",subject: 'Retail Portal | Build Success', to:"${devTeamEmails}"
									}
			failure{
					emailext body: "The Build number: ($BUILD_NUMBER) for  Code Challenge  portal pipeline: ($JOB_NAME) failed.\r\nPlease fix the issue to proceed with the build  URL: $JENKINSURL \r\nLink: $emailLink\r\nEnvironment: $emailEnv \n Sonar URL: $SonarURL",subject: 'Retail Portal | Build Failed', to:"${devTeamEmails}"
								}
        
        }


		}
		
		
		stage('Re-Deploy Portal') {
agent { label "${environment}" }
when { expression { build_ok != "true" } }
steps {
    
dir("${HTDOCS_PATH}"){
script{
//catchError(buildResult: 'SUCCESS', stageResult: 'NOT_BUILT') {
if (build_ok == "true" ) {
echo 'Deployment is Success, skipping Re-Deloy'
}
else {
    
//CopytheartifactsfromtheprevioussuccessfulbuildinJenkins
echo "Since current deployment has failed, Rollback to last successful build : ${lastSuccessfulBuildID}"
copyArtifacts projectName: env.JOB_NAME, selector: [$class: 'SpecificBuildSelector', buildNumber: "${lastSuccessfulBuildID}"], filter: 'tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz', fingerprintArtifacts: true
echo'Downloadedfilefrompreviousbuild'

def now = new Date()
def nowFormat = now.format("yyyyMMddHHmm")
sh """
gzip -fd tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tgz
tar -xvf tmdb-devops-challenge-1.0.4-dt-PC-SNAPSHOT.tar
mv tmdb-devops-challenge tmdb-devops-challenge_$nowFormat
chmod -R 755 tmdb-devops-challenge
"""
JiraStep = "BuildFailed"
}
//}
}
}
}
post {  
        always{
                script {
					if ( "${environment}" == 'SI1') {
						emailLink="https://sit.tmdb-devops.com/portalnew/"
						emailEnv="SIT1"
						echo 'RM-2 SIT1 deployment Done'
						} else if( "${environment}" == 'tmdb-devops-omnuatap2'){
						emailLink="https://sit.tmdb-devops.com/business2/"
						emailEnv="SIT2"
							echo 'RM-2 SIT2 deployment Done'
						}else if( "${environment}" == 'tmdb-devops-UAT1-smeuatwb2-FE' ){
						emailLink="https://uat.tmdb-devops.com/business/"
						emailEnv="UAT1"
							echo 'RM-2 UAT1 deployment Done'
						}else if( "${environment}" == 'tmdb-devops-UAT2-omnuatap1' ){
						emailLink="https://uat.tmdb-devops.com/business2/"
						emailEnv="UAT2"
							echo 'RM-2 UAT2 deployment Done'
						}

                }
        }
						
                            success{
					emailext body: "The current Build : ($BUILD_NUMBER) has failed for tmdb-devops portal pipeline and Rollbacked successfully to (${lastSuccessfulBuildID}) .\n URL: $JENKINSURL \r\nLink: $emailLink\r\nEnvironment: $emailEnv \n Sonar URL: $SonarURL",subject: 'tmdb-devops Portal | Build Success', to:"${devTeamEmails}"
									}
			failure{
					emailext body: "The Build number: ($BUILD_NUMBER) for tmdb-devops portal pipeline: ($JOB_NAME)  Rollback failed.\r\nPlease fix the issue to proceed with the build  URL: $JENKINSURL \r\nLink: $emailLink\r\nEnvironment: $emailEnv \n Sonar URL: $SonarURL",subject: 'tmdb-devops Portal | Build Failed', to:"${devTeamEmails}"
								}
						
                }
    }



   
    stage('Sanity check') {
    agent { any }
    steps {
      script {
					
	     
	      
	      if ( "${environment}" == 'SI1') {
						emailLink="https://sit.tmdb-devops.com/portalnew/"
						sh "curl $emailLink"
						} else if( "${environment}" == 'tmdb-devops-omnuatap2'){
						emailLink="https://sit.tmdb-devops.com/business2/"
						sh "curl $emailLink"
						}else if( "${environment}" == tmdb-devops-UAT1-smeuatwb2-FE' ){
						emailLink="https://uat.tmdb-devops.com/business/"
						sh "curl $emailLink"
						}else if( "${environment}" == 'tmdb-devops-UAT2-omnuatap1' ){
						emailLink="https://uat.tmdb-devops.com/business2/"
						sh "curl $emailLink"
						}
				
				
            }    
       }
    }


    stage ('Web Automation Test') {
	agent {"NPE_SLAVE" }
	environment {
	PATH="C:\\Windows\\System32;C:\\Program Files\\Git\\cmd;C:\\Program Files\\Maven\\apache-maven-3.8.1\\bin;"
        JAVA_HOME="C:\\Program Files\\Java\\jdk-11.0.17"
        LC_ALL="C.UTF-8"
  	}
   steps {
      bat 'dir /b'
       bat 'rd /S /Q web-automation'
      echo '* Cloning repository'
               bat 'git clone -b veeragiri-patch-2 https://veera.mangipudi:ODQyNDAxNjE1NjA5OkVCW+j+WwIL8wmINTZOffEJOmlN@testautomation.bitbucket.com/scm/automation/web-automation.git'
                 // bat 'cd WEB-Automation'
                  bat 'dir /b'
      echo '* Starting the Test'
                  bat 'mvn -U -s D:/Jenkins_Slave/settings.xml -f web-automation/pom.xml clean test'
                           
                            echo '* Test Completed'
       
   }
   }
   
 stage('Jira Update'){
        agent { label 'master' }
         
              steps {
                
               
                // comment_issues()
           
              
             
               echo 'Updating Jira'
               // withCredentials([usernamePassword(credentialsId: 'veerajiracred', usernameVariable: 'username', passwordVariable: 'password')]){
                   sh """
                      curl --insecure -D-  --header "Authorization: Basic SklSQS1BdXRvuOmF1dG9tYXRpb25AMTIz"  --header "Content-Type: application/json"  -X POST --data "{\\\"body\\\": \\\"$JiraStep.\\\"}" -H 'Content-Type: application/json' https://jira.test.com/rest/api/2/issue/$issueId/comment 
                      """
                    //}
              
              echo 'Updated Jira'
              
              
            }
       
   }
}
}
