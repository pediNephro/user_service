pipeline {
    agent any

    environment {
        DOCKER_HUB_REPO = 'azizos07/user-service'
        SERVICE_NAME = 'user-service'
        SONAR_HOST = 'http://172.17.0.2:9000'
        MYSQL_CONTAINER = "test-mysql-${BUILD_NUMBER}"
        MYSQL_PORT = '3307'
        MYSQL_DATABASE = 'testdb'
        MYSQL_ROOT_PASSWORD = 'root'
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

        stage('Start Test MySQL') {
    steps {
        sh """
            docker run -d \
                --name ${MYSQL_CONTAINER} \
                -e MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD} \
                -e MYSQL_DATABASE=${MYSQL_DATABASE} \
                -e MYSQL_ROOT_HOST='%' \
                -p ${MYSQL_PORT}:3306 \
                mysql:8

            echo "Waiting for MySQL port to be reachable..."
            for i in \$(seq 1 60); do
                if docker run --rm --network host mysql:8 mysqladmin ping \
                    -h 127.0.0.1 \
                    -P ${MYSQL_PORT} \
                    -u root \
                    -p${MYSQL_ROOT_PASSWORD} \
                    --silent 2>/dev/null; then
                    echo "MySQL is ready after \${i} seconds"
                    break
                fi
                if [ \$i -eq 60 ]; then
                    echo "=== MySQL container logs ==="
                    docker logs ${MYSQL_CONTAINER}
                    echo "MySQL failed to start in 60 seconds"
                    exit 1
                fi
                echo "Waiting... (\${i}/60)"
                sleep 2
            done
        """
    }
}

        stage('Unit Tests') {
            steps {
                sh """
                    mvn test \
                        -Dspring.datasource.url=jdbc:mysql://localhost:${MYSQL_PORT}/${MYSQL_DATABASE} \
                        -Dspring.datasource.username=root \
                        -Dspring.datasource.password=${MYSQL_ROOT_PASSWORD} \
                        -Dspring.jpa.hibernate.ddl-auto=create-drop \
                        -Dspring.jpa.database-platform=org.hibernate.dialect.MySQL8Dialect
                """
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

    post {
        always {
            sh """
                echo "Cleaning up MySQL container..."
                docker stop ${MYSQL_CONTAINER} || true
                docker rm   ${MYSQL_CONTAINER} || true
            """
        }
        success {
            echo "Pipeline completed successfully"
        }
        failure {
            echo "Pipeline failed — MySQL container cleaned up"
        }
    }
}