pipeline {
    agent any

    environment {
        // Docker Hub Configuration
        DOCKER_HUB_REPO = 'pedinephro/user-service'
        DOCKER_HUB_CREDS = credentials('dockerhub-creds')

        // Build Configuration
        JAVA_VERSION = '17'
        MAVEN_VERSION = '3.9.0'
        SERVICE_NAME = 'user-service'
        SERVICE_PORT = '8081'
    }

    stages {
        stage('Checkout') {
            steps {
                echo "=== Cloning Repository ==="
                checkout scm
                sh 'git log -1 --pretty=format:"%H %s"'
            }
        }

        stage('Build') {
            steps {
                echo "=== Building with Maven ==="
                sh '''
                    mvn clean package -DskipTests \
                        -Dmaven.compiler.source=17 \
                        -Dmaven.compiler.target=17
                '''
            }
        }

        stage('Unit Tests') {
            steps {
                echo "=== Running Unit Tests ==="
                sh '''
                    mvn test \
                        -Dtest=**/*Test.class \
                        -Dgroups!="integration"
                '''
            }
            post {
                always {
                    junit 'target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
            when {
                branch 'main'
            }
            steps {
                echo "=== Code Quality Analysis ==="
                sh '''
                    mvn sonar:sonar \
                        -Dsonar.projectKey=user-service \
                        -Dsonar.host.url=http://sonarqube:9000 \
                        -Dsonar.login=${SONAR_TOKEN}
                '''
            }
        }

        stage('Build Docker Image') {
            steps {
                echo "=== Building Docker Image ==="
                script {
                    sh '''
                        docker build -t ${DOCKER_HUB_REPO}:${BUILD_NUMBER} .
                        docker tag ${DOCKER_HUB_REPO}:${BUILD_NUMBER} ${DOCKER_HUB_REPO}:latest
                    '''
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                echo "=== Pushing Image to Docker Hub ==="
                script {
                    sh '''
                        echo $DOCKER_HUB_CREDS_PSW | docker login -u $DOCKER_HUB_CREDS_USR --password-stdin
                        docker push ${DOCKER_HUB_REPO}:${BUILD_NUMBER}
                        docker push ${DOCKER_HUB_REPO}:latest
                        docker logout
                    '''
                }
            }
        }

        stage('Deploy (Optional)') {
            when {
                branch 'main'
            }
            steps {
                echo "=== Deploying to Staging ==="
                sh '''
                    # Example: Deploy to staging environment
                    echo "Deployment script would go here"
                    # docker pull ${DOCKER_HUB_REPO}:latest
                    # docker run -d -p ${SERVICE_PORT}:8080 ${DOCKER_HUB_REPO}:latest
                '''
            }
        }
    }

    post {
        success {
            echo "✅ Pipeline SUCCESS"
            // Send notification
        }
        failure {
            echo "❌ Pipeline FAILED"
            // Send notification
        }
        always {
            sh 'docker logout'
            cleanWs()
        }
    }
}