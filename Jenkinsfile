// =============================================================================
// Git-To-Go — Jenkins CI/CD Pipeline
//
// Stages:
//   1. Checkout     → Code pull from GitHub
//   2. Test Backend → Maven tests with PostgreSQL
//   3. Test Frontend→ npm lint + build
//   4. Build Images → Docker multi-stage build (backend + frontend)
//   5. Push Images  → Push to Docker Hub / GHCR
//   6. Deploy       → docker compose up on server
//
// Required Jenkins Credentials:
//   - docker-hub-creds    : Docker Hub username/password (or GHCR token)
//   - server-ssh-key      : SSH private key for production server
//   - git2go-env-file     : .env file content (secrets)
//
// Required Jenkins Tools:
//   - JDK 17 (named 'JDK-17')
//   - NodeJS 18 (named 'NodeJS-18')
//   - Docker (available on agent)
// =============================================================================

pipeline {
    agent any

    // ==================== TOOLS ====================
    tools {
        jdk 'JDK-17'
        nodejs 'NodeJS-18'
    }

    // ==================== ENVIRONMENT ====================
    environment {
        DOCKER_REGISTRY = 'docker.io'
        DOCKER_IMAGE_BACKEND = 'manishk57107/git2go-backend'
        DOCKER_IMAGE_FRONTEND = 'manishk57107/git2go-frontend'
        IMAGE_TAG = "${env.BUILD_NUMBER}-${env.GIT_COMMIT?.take(7) ?: 'unknown'}"
    }

    // ==================== OPTIONS ====================
    options {
        timestamps()                     // Log mein timestamp dikhao
        timeout(time: 30, unit: 'MINUTES') // Max 30 min — isse zyada toh kuch galat hai
        disableConcurrentBuilds()        // Ek time pe ek hi build
        buildDiscarder(logRotator(numToKeepStr: '10')) // Last 10 builds rakh
    }

    // ==================== TRIGGERS ====================
    triggers {
        // GitHub webhook trigger — push hone pe automatically build
        githubPush()
    }

    // ==================== STAGES ====================
    stages {

        // ========== STAGE 1: Checkout ==========
        stage('Checkout') {
            steps {
                checkout scm
                echo "Branch: ${env.GIT_BRANCH}"
                echo "Commit: ${env.GIT_COMMIT}"
            }
        }

        // ========== STAGE 2: Test Backend ==========
        stage('Test Backend') {
            steps {
                dir('backend') {
                    echo '===== Running Backend Tests ====='
                    sh 'chmod +x mvnw'
                    sh './mvnw clean verify -B'
                }
            }
            post {
                always {
                    // Test results publish karo Jenkins UI mein
                    junit(
                        testResults: 'backend/target/surefire-reports/*.xml',
                        allowEmptyResults: true
                    )
                }
            }
        }

        // ========== STAGE 3: Test Frontend ==========
        stage('Test Frontend') {
            steps {
                dir('frontend') {
                    echo '===== Running Frontend Tests ====='
                    sh 'npm ci'
                    sh 'npm run lint'
                    sh 'npm run build'
                }
            }
        }

        // ========== STAGE 4: Build Docker Images ==========
        stage('Build Docker Images') {
            parallel {
                // Backend aur Frontend SIMULTANEOUSLY build — time save
                stage('Build Backend Image') {
                    steps {
                        echo "===== Building Backend Image: ${DOCKER_IMAGE_BACKEND}:${IMAGE_TAG} ====="
                        sh """
                            docker build \
                                -t ${DOCKER_IMAGE_BACKEND}:${IMAGE_TAG} \
                                -t ${DOCKER_IMAGE_BACKEND}:latest \
                                ./backend
                        """
                    }
                }
                stage('Build Frontend Image') {
                    steps {
                        echo "===== Building Frontend Image: ${DOCKER_IMAGE_FRONTEND}:${IMAGE_TAG} ====="
                        sh """
                            docker build \
                                -t ${DOCKER_IMAGE_FRONTEND}:${IMAGE_TAG} \
                                -t ${DOCKER_IMAGE_FRONTEND}:latest \
                                ./frontend
                        """
                    }
                }
            }
        }

        // ========== STAGE 5: Push Images to Registry ==========
        stage('Push Images') {
            when {
                branch 'main'  // Sirf main branch pe push — PR pe nahi
            }
            steps {
                echo '===== Pushing Images to Docker Hub ====='
                withCredentials([usernamePassword(
                    credentialsId: 'docker-hub-creds',
                    usernameVariable: 'DOCKER_USER',
                    passwordVariable: 'DOCKER_PASS'
                )]) {
                    sh '''
                        echo "$DOCKER_PASS" | docker login -u "$DOCKER_USER" --password-stdin

                        docker push ${DOCKER_IMAGE_BACKEND}:${IMAGE_TAG}
                        docker push ${DOCKER_IMAGE_BACKEND}:latest

                        docker push ${DOCKER_IMAGE_FRONTEND}:${IMAGE_TAG}
                        docker push ${DOCKER_IMAGE_FRONTEND}:latest

                        docker logout
                    '''
                }
            }
        }

        // ========== STAGE 6: Deploy to Server ==========
        stage('Deploy') {
            when {
                branch 'main'
            }
            steps {
                echo '===== Deploying to Production ====='
                sshagent(credentials: ['server-ssh-key']) {
                    sh '''
                        ssh -o StrictHostKeyChecking=no ${DEPLOY_USER}@${DEPLOY_HOST} << 'DEPLOY_SCRIPT'
                            set -e

                            cd ~/Git-To-Go

                            # Pull latest code
                            git pull origin main

                            # Pull latest images
                            docker compose pull backend frontend

                            # Deploy with zero downtime
                            docker compose up -d --remove-orphans

                            # Cleanup old images
                            docker image prune -f

                            # Health check
                            echo "Waiting for services..."
                            sleep 15

                            # Verify backend is healthy
                            if curl -sf http://localhost:8080/actuator/health > /dev/null; then
                                echo "Backend: HEALTHY"
                            else
                                echo "Backend: UNHEALTHY — rolling back"
                                docker compose down
                                git checkout HEAD~1
                                docker compose up -d
                                exit 1
                            fi

                            echo "Deployment successful!"
                        DEPLOY_SCRIPT
                    '''
                }
            }
        }
    }

    // ==================== POST ACTIONS ====================
    post {
        success {
            echo "Pipeline SUCCESS — Build #${env.BUILD_NUMBER}"
        }
        failure {
            echo "Pipeline FAILED — Build #${env.BUILD_NUMBER}"
            // Email notification (uncomment when SMTP configured):
            // mail to: 'team@git2go.com',
            //      subject: "FAILED: ${env.JOB_NAME} #${env.BUILD_NUMBER}",
            //      body: "Check: ${env.BUILD_URL}"
        }
        always {
            // Workspace clean karo — disk space bachao
            cleanWs()
        }
    }
}
