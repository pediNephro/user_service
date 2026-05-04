pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'azizos07/user-service'
        SERVICE_NAME = 'user-service'
        SONAR_HOST = 'http://sonarqube:9000'
    }

    stages {

        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh 'mvn clean package -DskipTests'
            }
        }

        stage('Unit Tests') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/**/*.xml'
                }
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                    sh """
                        mvn sonar:sonar \
                        -Dsonar.projectKey=${SERVICE_NAME} \
                        -Dsonar.projectName=${SERVICE_NAME} \
                        -Dsonar.host.url=${SONAR_HOST} \
                        -Dsonar.login=${SONAR_TOKEN}
                    """
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh """
                    docker build -t ${DOCKER_HUB_REPO}:${BUILD_NUMBER} .
                    docker tag ${DOCKER_HUB_REPO}:${BUILD_NUMBER} ${DOCKER_HUB_REPO}:latest
                """
            }
        }

        stage('Push Docker') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub-creds',
                    usernameVariable: 'USER',
                    passwordVariable: 'PASS')]) {

                    sh """
                        echo $PASS | docker login -u $USER --password-stdin
                        docker push ${DOCKER_HUB_REPO}:${BUILD_NUMBER}
                        docker push ${DOCKER_HUB_REPO}:latest
                        docker logout
                    """
                }
            }
        }

        stage('Trigger Deploy') {
            steps {
                build job: 'user-service-deploy', parameters: [
                    string(name: 'BUILD_NUMBER', value: "${BUILD_NUMBER}")
                ]
            }
        }
    }
}
