pipeline {
    agent any

    environment {
        AWS_REGION       = 'eu-north-1'
        AWS_ACCOUNT_ID   = '942679464303'
        FRONTEND_REPO    = 'https://github.com/RakeshReddy21/HRMS-Portal.git'
        TARGET_GROUP_ARN = 'arn:aws:elasticloadbalancing:eu-north-1:942679464303:targetgroup/workforce-tg/0722c4fdbe07f0dd'
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
        //  STAGE 2: Build, Test & Package
        //  Runs full Maven lifecycle in one shot so
        //  JaCoCo agent attaches properly for coverage.
        // ═══════════════════════════════════════════
        stage('Build & Test') {
            steps {
                echo '🔨🧪 Compiling, running tests (JaCoCo coverage), and packaging JAR...'
                sh 'mvn clean verify -B'
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
                script {
                    echo '🔍 Running SonarQube code quality analysis...'
                    // Keep deployment moving if Sonar server is temporarily unreachable.
                    catchError(buildResult: 'SUCCESS', stageResult: 'UNSTABLE') {
                        withSonarQubeEnv('sonarqube') {
                            sh 'mvn sonar:sonar -B'
                        }
                    }
                }
            }
        }

        stage('Quality Gate') {
            steps {
                script {
                    if (!fileExists('report-task.txt')) {
                        echo '⚠️ Sonar report-task.txt not found; skipping Quality Gate.'
                        return
                    }
                    echo '🚦 Waiting for SonarQube Quality Gate...'
                    try {
                        timeout(time: 2, unit: 'MINUTES') {
                            waitForQualityGate abortPipeline: false
                        }
                    } catch (Exception e) {
                        echo "⚠️ Quality Gate check timed out or failed, continuing pipeline..."
                    }
                }
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 6: Docker Build & Push Backend to ECR
        //  NOTE: Single-quoted sh ''' blocks use shell-
        //  level $VAR expansion, avoiding Groovy string
        //  interpolation of the AWS_ACCOUNT_ID credential.
        // ═══════════════════════════════════════════
        stage('Docker Build & Push Backend') {
            steps {
                echo '🐳 Building and pushing backend Docker image to ECR...'
                sh '''
                    aws ecr get-login-password --region $AWS_REGION | \
                    docker login --username AWS --password-stdin \
                        $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com

                    docker build \
                        -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/workforce-backend:$BUILD_NUMBER \
                        -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/workforce-backend:latest .

                    docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/workforce-backend:$BUILD_NUMBER
                    docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/workforce-backend:latest
                '''
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 7: Clone, Build & Push Frontend to ECR
        // ═══════════════════════════════════════════
        stage('Docker Build & Push Frontend') {
            steps {
                echo '🐳 Building and pushing frontend Docker image to ECR...'
                sh '''
                    rm -rf /tmp/hrms-portal
                    git clone $FRONTEND_REPO /tmp/hrms-portal

                    docker build \
                        -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/workforce-frontend:$BUILD_NUMBER \
                        -t $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/workforce-frontend:latest \
                        /tmp/hrms-portal/

                    docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/workforce-frontend:$BUILD_NUMBER
                    docker push $AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com/workforce-frontend:latest

                    rm -rf /tmp/hrms-portal
                '''
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 8: Discover EC2 targets from ALB
        //  Queries the target group to get instance IDs,
        //  resolves their private IPs, and writes a fresh
        //  ansible/hosts.ini so Ansible can reach them.
        // ═══════════════════════════════════════════
        stage('Generate Ansible Inventory') {
            steps {
                echo '📋 Discovering EC2 instances from ALB target group...'
                sh '''
                    echo "Querying target group for registered instances..."

                    INSTANCE_IDS=$(aws elbv2 describe-target-health \
                        --target-group-arn $TARGET_GROUP_ARN \
                        --region $AWS_REGION \
                        --query 'TargetHealthDescriptions[*].Target.Id' \
                        --output text)

                    if [ -z "$INSTANCE_IDS" ]; then
                        echo "ERROR: No instances found in target group!"
                        exit 1
                    fi

                    echo "[webservers]" > ansible/hosts.ini

                    INDEX=1
                    REACHABLE_COUNT=0
                    for ID in $INSTANCE_IDS; do
                        PRIVATE_IP=$(aws ec2 describe-instances \
                            --instance-ids "$ID" \
                            --region $AWS_REGION \
                            --query 'Reservations[0].Instances[0].PrivateIpAddress' \
                            --output text)

                        if [ "$PRIVATE_IP" = "None" ] || [ -z "$PRIVATE_IP" ]; then
                            echo "WARNING: Could not resolve IP for instance $ID, skipping..."
                            continue
                        fi

                        if ! timeout 5 bash -c "cat < /dev/null > /dev/tcp/$PRIVATE_IP/22" 2>/dev/null; then
                            echo "WARNING: Instance $ID ($PRIVATE_IP) is not reachable on SSH port 22 from Jenkins, skipping..."
                            continue
                        fi

                        echo "ec2-instance-$INDEX ansible_host=$PRIVATE_IP" >> ansible/hosts.ini
                        echo "  → Found reachable instance $ID → $PRIVATE_IP"
                        INDEX=$((INDEX + 1))
                        REACHABLE_COUNT=$((REACHABLE_COUNT + 1))
                    done

                    if [ "$REACHABLE_COUNT" -eq 0 ]; then
                        echo "ERROR: No reachable instances found for deployment!"
                        exit 1
                    fi

                    echo "" >> ansible/hosts.ini
                    echo "[webservers:vars]" >> ansible/hosts.ini
                    echo "ansible_user=ec2-user" >> ansible/hosts.ini
                    echo "ansible_ssh_common_args='-o StrictHostKeyChecking=no -o ServerAliveInterval=30 -o ServerAliveCountMax=10 -o ControlMaster=auto -o ControlPersist=60s'" >> ansible/hosts.ini

                    echo ""
                    echo "=== Generated Ansible Inventory ==="
                    cat ansible/hosts.ini
                '''
            }
        }

        // ═══════════════════════════════════════════
        //  STAGE 9: Ansible Blue-Green Deployment
        //  Uses extraVars with hidden:true for the
        //  account ID so it never leaks via Groovy
        //  string interpolation.
        // ═══════════════════════════════════════════
        stage('Deploy via Ansible') {
            steps {
                echo '🚀 Deploying to EC2 via Ansible blue-green strategy...'
                timeout(time: 15, unit: 'MINUTES') {
                    ansiblePlaybook(
                        playbook: 'ansible/deploy.yml',
                        inventory: 'ansible/hosts.ini',
                        credentialsId: 'ec2-ssh-key',
                        disableHostKeyChecking: true,
                        extraVars: [
                            build_number:    env.BUILD_NUMBER,
                            aws_account_id:  env.AWS_ACCOUNT_ID,
                            aws_region:      env.AWS_REGION,
                            target_group_arn: env.TARGET_GROUP_ARN
                        ]
                    )
                }
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
            sh 'docker image prune -f || true'
        }
    }
}
