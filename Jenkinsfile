// 자주 사용되는 필요한 변수를 전역으로 선언하는 것도 가능.
// 이후 쉘 스크립트에서 사용할 ECR 인증 헬퍼 이름
def ecrLoginHelper = "docker-credential-ecr-login" // ECR credential helper 이름
def deployHost = "172.31.10.176" // 배포 인스턴스의 private 주소

pipeline {
  agent any

  environment {
    SERVICE_DIRS = "config-service,discovery-service,gateway-service,user-service,ordering-service,product-service"
    ECR_URL = "390844784325.dkr.ecr.ap-northeast-2.amazonaws.com"
    REGION = "ap-northeast-2"

    // Jenkins Credentials에서 가져오기
    GIT_USERNAME = credentials('config-git-username')
    GIT_PASSWORD = credentials('config-git-password')
  }

  stages {

    stage('Generate application-dev.yml') {
      steps {
        script {
          def configYml = """
          server:
            port: 8888

          spring:
            application:
              name: config-service
            cloud:
              config:
                server:
                  git:
                    uri: https://github.com/uBing9201/git-local-repo.git
                    default-label: main
                    username: ${GIT_USERNAME}
                    password: ${GIT_PASSWORD}

          management:
            endpoints:
              web:
                exposure:
                  include: health, refresh, beans
          """
          // 파일을 깃에 커밋하지 않고 워크스페이스에만 생성
          writeFile file: 'config-service/src/main/resources/application-dev.yml', text: configYml
        }
      }
    }

    stage('Build Changed Services') {
      steps {
        script {
          sh """
            cd config-service
            ./gradlew clean build -x test
          """
        }
      }
    }

    stage('Build and Push Docker Image') {
      steps {
        script {
          withAWS(region: "${REGION}", credentials: "aws-key") {
            sh """
              docker build -t config-service:latest config-service
              docker tag config-service:latest ${ECR_URL}/config-service:latest
              docker push ${ECR_URL}/config-service:latest
            """
          }
        }
      }
    }

    stage('Deploy to Server') {
      steps {
        sshagent(credentials: ["deploy-key"]) {
          sh """
          scp -o StrictHostKeyChecking=no config-service/src/main/resources/application-dev.yml ubuntu@${deployHost}:/home/ubuntu/config-service/src/main/resources/application-dev.yml

          ssh -o StrictHostKeyChecking=no ubuntu@${deployHost} '
            cd /home/ubuntu && \
            aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_URL} && \
            docker-compose pull config-service && \
            docker-compose up -d config-service
          '
          """
        }
      }
    }
  }
}