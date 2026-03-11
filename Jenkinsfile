pipeline {
    agent any

    environment {
        AWS_REGION         = 'eu-north-1'
        AWS_ACCOUNT_ID     = credentials('aws-account-id')
        ECR_BACKEND        = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/workforce-backend"
        ECR_FRONTEND       = "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/workforce-frontend"
        SONAR_HOST_URL     = 'http://localhost:9000'
        FRONTEND_REPO      = 'https://github.com/RakeshReddy21/HRMS-Portal.git'
    }

    tools {
        maven 'Maven-3.8'
    }

    stages {

        // ═══════════════════════════════════════════
        //  STAGE 1: Git Checkout (Backend)
        // ═══════════════════════════════════════════
        stage('Git Checkout') {
            steps {
                echo '📥 Checking out backend source code...'
                checkout scm
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 2: Maven Build (Compile)
        // ═══════════════════════════════════════════
        stage('Maven Build') {
            steps {
                echo '🔨 Compiling backend source code...'
                sh 'mvn clean compile -B'
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 3: Unit Tests (JUnit + Mockito)
        // ═══════════════════════════════════════════
        stage('Unit Tests') {
            steps {
                echo '🧪 Running JUnit/Mockito tests...'
                sh 'mvn test -B'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: '**/target/surefire-reports/*.xml'
                    jacoco execPattern: '**/target/jacoco.exec',
                           classPattern: '**/target/classes',
                           sourcePattern: '**/src/main/java'
                }
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 4: SonarQube Code Quality Analysis
        // ═══════════════════════════════════════════
        stage('SonarQube Analysis') {
            steps {
                echo '🔍 Running SonarQube code quality analysis...'
                withSonarQubeEnv('sonarqube') {
                    sh 'mvn sonar:sonar -B'
                }
            }
        }

        stage('Quality Gate') {
            steps {
                echo '🚦 Waiting for SonarQube Quality Gate...'
                timeout(time: 5, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 5: Package JAR
        // ═══════════════════════════════════════════
        stage('Package JAR') {
            steps {
                echo '📦 Packaging Spring Boot JAR...'
                sh 'mvn package -Dmaven.test.skip=true -B'
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 6: Docker Build & Push Backend to ECR
        // ═══════════════════════════════════════════
        stage('Docker Build & Push Backend') {
            steps {
                echo '🐳 Building and pushing backend Docker image to ECR...'
                script {
                    sh """
                        aws ecr get-login-password --region ${AWS_REGION} | \
                        docker login --username AWS --password-stdin ${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com

                        docker build -t ${ECR_BACKEND}:${BUILD_NUMBER} -t ${ECR_BACKEND}:latest .

                        docker push ${ECR_BACKEND}:${BUILD_NUMBER}
                        docker push ${ECR_BACKEND}:latest
                    """
                }
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 7: Clone, Build & Push Frontend to ECR
        // ═══════════════════════════════════════════
        stage('Docker Build & Push Frontend') {
            steps {
                echo '🐳 Building and pushing frontend Docker image to ECR...'
                script {
                    sh """
                        rm -rf /tmp/hrms-portal
                        git clone ${FRONTEND_REPO} /tmp/hrms-portal

                        docker build -t ${ECR_FRONTEND}:${BUILD_NUMBER} -t ${ECR_FRONTEND}:latest /tmp/hrms-portal/

                        docker push ${ECR_FRONTEND}:${BUILD_NUMBER}
                        docker push ${ECR_FRONTEND}:latest

                        rm -rf /tmp/hrms-portal
                    """
                }
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 8: Ansible Blue-Green Deployment
        // ═══════════════════════════════════════════
        stage('Deploy via Ansible') {
            steps {
                echo '🚀 Deploying to EC2 via Ansible blue-green strategy...'
                ansiblePlaybook(
                    playbook: 'ansible/deploy.yml',
                    inventory: 'ansible/hosts.ini',
                    credentialsId: 'ec2-ssh-key',
                    extras: "-e build_number=${BUILD_NUMBER} -e aws_account_id=${AWS_ACCOUNT_ID} -e aws_region=${AWS_REGION}"
                )
            }
        }
    }

    post {
        success {
            echo '✅ Pipeline completed successfully! Application deployed.'
        }
        failure {
            echo '❌ Pipeline failed! Check the logs above for details.'
        }
        always {
            // Clean up Docker images to save disk space
            sh 'docker image prune -f || true'
        }
    }
}

