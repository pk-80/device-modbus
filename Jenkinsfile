pipeline {
    agent any
    stages{
        stage ('Unit test') {
            agent {
                docker {
                   image 'maven:3-jdk-8'
                   args  '-v $HOME/.m2:/var/maven/.m2 -e MAVEN_CONFIG=/var/maven/.m2'
                }
            }
            steps {
                sh "mvn -Duser.home=/var/maven -f iotech-pom.xml -U clean test"
            }
        }
        stage ('Deploy Artifact') {
//            when {
//                branch 'master'
//            }
            agent {
                docker {
                   image 'maven:3-jdk-8'
                   args  '-v $HOME/.m2:/var/maven/.m2 -e MAVEN_CONFIG=/var/maven/.m2'
                }
            }
            steps {
                sh "mvn -Duser.home=/var/maven -f iotech-pom.xml -U -Dmaven.test.skip=true deploy"
            }
        }

        stage ('Build & Deploy Docker image') {
//            when {
//                branch 'master'
//            }
            agent any
            steps {
                script {
                    sh "mv ./target/*.jar ./iotech-docker-files/"
                    try {
                        sh "docker rmi docker.iotechsys.com/edgexpert/device-modbus:built"
                    } catch (exc) {
                        echo "exception happens during remove the older docker image, ignoring the exception"
                    }
                    sh "docker build --tag docker.iotechsys.com/edgexpert/device-modbus:built ./iotech-docker-files"
                    sh "docker push docker.iotechsys.com/edgexpert/device-modbus:built"
                }
            }

        }
    }
}