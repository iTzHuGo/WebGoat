pipeline {
    agent any

    // Name of the tool created in Jenkins Tools
    tools {
        nodejs 'Node22' 
    }

    stages {
        stage('Checkout') {
            steps {
                // Pull code from Git repository
                checkout scm
            }
        }
        
        stage('Build & Install Dependencies') {
            steps {
                echo "Building the application using Node 22..."
                sh 'npm install'
            }
        }

        stage('Security Scanning (SAST)') {
            parallel {
                
                // --- Stage 2a: Agnostic Security (Semgrep via Docker) ---
                stage('Semgrep SAST') {
                    steps {
                        echo "Executing Semgrep SAST scan via Docker..."
                        // We mount the Jenkins workspace into the Semgrep container
                        sh '''
                        docker run --rm \
                          -v $(pwd):/src \
                          -w /src \
                          returntocorp/semgrep:latest \
                          semgrep scan --json --output sast-results.json --config="p/javascript" --config="p/owasp-top-ten" --exclude="codeql-db" --exclude="node_modules" --exclude="codeql-results.sarif" --exclude="codeql" .
                          
                        '''
                    }
                    post {
                        always {
                            // Save the JSON artifact to the Jenkins UI
                            archiveArtifacts artifacts: 'sast-results.json', allowEmptyArchive: true
                        }
                    }
                }

                // --- Stage 2b: Proprietary Security (CodeQL CLI - Segmented) ---
                stage('CodeQL SAST') {
                    environment {
                        // Defined here so it is securely accessible across all sub-stages
                        CQ_TMP = "/tmp/codeql-thesis-${env.BUILD_NUMBER}"
                    }
                    stages {
                        stage('CodeQL: Download & Setup') {
                            steps {
                                echo "Downloading CodeQL CLI Bundle..."
                                sh '''
                                mkdir -p ${CQ_TMP}
                                wget -q https://github.com/github/codeql-action/releases/latest/download/codeql-bundle-linux64.tar.gz -O ${CQ_TMP}/codeql-bundle.tar.gz
                                tar -xzf ${CQ_TMP}/codeql-bundle.tar.gz -C ${CQ_TMP}
                                '''
                            }
                        }

                        stage('CodeQL: Create DB') {
                            steps {
                                echo "Creating CodeQL Database..."
                                // Using explicit path execution for stability across stages
                                sh '${CQ_TMP}/codeql/codeql database create ${CQ_TMP}/codeql-db --language=javascript-typescript --source-root .'
                            }
                        }

                        stage('CodeQL: Analyze') {
                            steps {
                                echo "Running CodeQL Analysis (Unleashed!)..."
                                sh '''
                                ${CQ_TMP}/codeql/codeql database analyze ${CQ_TMP}/codeql-db javascript-security-extended.qls \
                                  --format=sarif-latest \
                                  --output=$(pwd)/codeql-results.sarif \
                                  --ram=12000 \
                                  --threads=8
                                '''
                            }
                        }
                    }
                    post {
                        always {
                            echo "CodeQL Post-Actions: Saving Artifacts & Running Cleanup..."
                            // Save the SARIF artifact to the Jenkins UI
                            archiveArtifacts artifacts: 'codeql-results.sarif', allowEmptyArchive: true
                            // Put the cleanup step in always block to prevent storage leaks if a sub-stage crashes
                            sh 'rm -rf ${CQ_TMP}'
                        }
                    }
                }

                // --- Stage 2c: SonarQube
                stage('SonarQube SAST') {
                    steps {
                        echo "Executing SonarQube Analysis..."
                        
                        // We MUST use a script block to declare variables in a Declarative Pipeline
                        script {
                            def scannerHome = tool 'SonarScanner'
                            
                            // Wraps the execution using the server credentials
                            withSonarQubeEnv('Sonar-VM') {
                                sh "${scannerHome}/bin/sonar-scanner \
                                    -Dsonar.projectKey=juice-shop-thesis \
                                    -Dsonar.sources=. \
                                    -Dsonar.exclusions=**/codeql/**,**/codeql-db/**,**/*.tar.gz,**/node_modules/**,**/sast-results.json,**/codeql-results.sarif \
                                    -Dsonar.host.url=https://sonarqube.dei.uc.pt \
                                    -Dsonar.javascript.node.maxspace=3072"
                            }
                        }
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo "DevSecOps Pipeline execution complete."
        }
    }
}