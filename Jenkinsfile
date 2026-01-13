pipeline {
    agent any

    environment {
        IMAGE_NAME = "hossammoh/tmdb-devops-challenge:${env.BRANCH_NAME}"
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Install Dependencies') {
            steps {
                script {
                    if (fileExists('node_modules')) {
                        echo "Skipping npm install since node_modules already exists."
                    } else {
                        sh 'npm ci'
                    }
                }
            }
        }

        stage('Setup ESLint') {
            steps {
                script {
                    // Install ESLint and plugins with --legacy-peer-deps flag
                    sh 'npm install --save-dev eslint @typescript-eslint/parser @typescript-eslint/eslint-plugin eslint-plugin-react eslint-plugin-react-hooks eslint-plugin-jsx-a11y eslint-plugin-import --legacy-peer-deps'

                    // Add basic ESLint configuration file
                    writeFile file: '.eslintrc.json', text: '''{
                        "parser": "@typescript-eslint/parser",
                        "extends": [
                          "eslint:recommended",
                          "plugin:react/recommended",
                          "plugin:@typescript-eslint/recommended"
                        ]
                      }'''

                    // Modify package.json to add lint script
                    def packageJson = readJSON file: 'package.json'
                    packageJson.scripts.lint = "eslint 'src/**/*.{js,jsx,ts,tsx}'"
                    writeJSON file: 'package.json', json: packageJson, pretty: 4
                }
            }
        }

        stage('Build') {
            steps {
                sh 'npm run build'
                archiveArtifacts artifacts: 'build/**', allowEmptyArchive: true
            }
        }

        stage('Lint') {
            steps {
                sh 'npm run lint'
            }
        }

        stage('TypeScript') {
            steps {
                sh 'npx tsc --noEmit'
            }
        }

        stage('Test') {
            steps {
                sh 'npm run test -- --watchAll=false'
                junit 'junit.xml'
            }
        }

        stage('Package') {
            when {
                branch 'main'
            }
            steps {
                script {
                    docker.withRegistry('https://${DOCKER_REGISTRY}', 'docker-credentials-id') {
                        def app = docker.build("${env.IMAGE_NAME}")
                        app.push()
                    }
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
        success {
            echo "Pipeline completed successfully!"
        }
        failure {
            echo "Pipeline failed."
        }
    }
}