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
            when {
                branch 'master'
            }
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
            when {
                branch 'master'
            }
            agent any
            steps {
                sh "mv ./target/*.jar ./iotech-docker-files/"
                sh "docker build --tag docker.iotechsys.com/edgex/device-modbus:built ./iotech-docker-files"
                sh "docker login -u bruce -p Txcx2sDHk5Ts3GO2 docker.iotechsys.com"
                sh "docker push docker.iotechsys.com/edgex/device-modbus:built"
                sh "docker logout docker.iotechsys.com"
            }

        }
    }
}