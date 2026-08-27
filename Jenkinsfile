pipeline {
    agent any

    // Ensure you have defined these matching names in Jenkins -> Manage Jenkins -> Tools
    tools {
        jdk 'Java25'     // WebGoat requires Java 17 or higher
        maven 'Maven3'   // Standard Maven installation
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }
        
        stage('Build & Compile Application') {
            steps {
                echo "Compiling WebGoat with Maven..."
                // We compile without running tests to establish a clean speed baseline
                sh 'mvn clean compile -DskipTests'
            }
        }

        stage('Security Scanning (SAST)') {
            parallel {
                
                // --- Stage 2a: Semgrep SAST ---
                stage('Semgrep SAST') {
                    steps {
                        echo "Executing Semgrep SAST scan via Docker..."
                        // Swapped target ruleset profile to Java
                        sh '''
                        docker run --rm \
                          -v $(pwd):/src \
                          -w /src \
                          returntocorp/semgrep:latest \
                          semgrep scan --json --output sast-results.json --config="p/java" --config="p/owasp-top-ten" --exclude="codeql-db" --exclude="target" --exclude="codeql-results.sarif" .
                        '''
                    }
                    post {
                        always {
                            archiveArtifacts artifacts: 'sast-results.json', allowEmptyArchive: true
                        }
                    }
                }

                // --- Stage 2b: CodeQL CLI ---
                stage('CodeQL SAST') {
                    environment {
                        CQ_TMP = "/tmp/codeql-webgoat-${env.BUILD_NUMBER}"
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
                                echo "Creating CodeQL Database (Tracing Compilation)..."
                                // CRITICAL: We explicitly supply the compile command so CodeQL can trace the build process
                                sh '${CQ_TMP}/codeql/codeql database create ${CQ_TMP}/codeql-db --language=java --command="mvn clean compile -DskipTests" --source-root .'
                            }
                        }

                        stage('CodeQL: Analyze') {
                            steps {
                                echo "Running CodeQL Java Analysis..."
                                // Swapped core query pack to java-security-extended
                                sh '''
                                ${CQ_TMP}/codeql/codeql database analyze ${CQ_TMP}/codeql-db java-security-extended.qls \
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
                            archiveArtifacts artifacts: 'codeql-results.sarif', allowEmptyArchive: true
                            sh 'rm -rf ${CQ_TMP}'
                        }
                    }
                }

                // --- Stage 2c: SonarQube ---
                stage('SonarQube SAST') {
                    steps {
                        echo "Executing SonarQube Java Analysis via Maven..."
                        withSonarQubeEnv('Sonar-VM') {
                            sh '''
                                mvn clean test-compile org.sonarsource.scanner.maven:sonar-maven-plugin:sonar \
                                    -DskipTests \
                                    -Dsonar.projectKey=webgoat-thesis
                            '''
                        }
                    }
                }
            }
        }
    }
    
    post {
        always {
            echo "WebGoat DevSecOps Pipeline execution complete."
        }
    }
}