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

    // 여기서 추가 시작
    GIT_USERNAME = credentials('config-git-username')
    GIT_PASSWORD = credentials('config-git-password')
    // 여기서 추가 끝
  }

  stages {

    // 여기서 추가 시작
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
          writeFile file: 'config-service/src/main/resources/application-dev.yml', text: configYml
        }
      }
    }
    // 여기서 추가 끝

    stage('Pull Codes from Github') {
      steps {
        checkout scm
      }
    }

    stage('Detect Changes') {
      steps {
        script {
          def commitCount = sh(script: "git rev-list --count HEAD", returnStdout: true).trim().toInteger()
          def changedServices = []
          def serviceDirs = env.SERVICE_DIRS.split(",")

          if (commitCount == 1) {
            echo "Initial commit detected. All services will be built."
            changedServices = serviceDirs
          } else {
            def changedFiles = sh(script: "git diff --name-only HEAD~1 HEAD", returnStdout: true)
                                .trim()
                                .split('\n')
            echo "Changed files: ${changedFiles}"
            serviceDirs.each { service ->
              if (changedFiles.any { it.startsWith(service + "/") }) {
                changedServices.add(service)
              }
            }
          }

          env.CHANGED_SERVICES = changedServices.join(",")

          if (env.CHANGED_SERVICES == "") {
            echo "No changes detected in service directories. Skipping build and deployment."
            currentBuild.result = 'SUCCESS'
          }
        }
      }
    }

    stage('Build Changed Services') {
      when {
        expression { env.CHANGED_SERVICES != "" }
      }
      steps {
        script {
          def changedServices = env.CHANGED_SERVICES.split(",")
          changedServices.each { service ->
            sh """
            echo "Building ${service}..."
            cd ${service}
            ./gradlew clean build -x test
            ls -al ./build/libs
            cd ..
            """
          }
        }
      }
    }

    stage('Build Docker Image & Push to AWS ECR') {
//       when {
//         expression { env.CHANGED_SERVICES != "" }
//       }
      steps {
        script {
          withAWS(region: "${REGION}", credentials: "aws-key") {
            def changedServices = env.SERVICE_DIRS.split(",")
            changedServices.each { service ->
              sh """
              curl -O https://amazon-ecr-credential-helper-releases.s3.us-east-2.amazonaws.com/0.4.0/linux-amd64/${ecrLoginHelper}
              chmod +x ${ecrLoginHelper}
              mv ${ecrLoginHelper} /usr/local/bin/

              mkdir -p ~/.docker
              echo '{"credHelpers": {"${ECR_URL}": "ecr-login"}}' > ~/.docker/config.json

              docker build -t ${service}:latest ${service}
              docker tag ${service}:latest ${ECR_URL}/${service}:latest
              docker push ${ECR_URL}/${service}:latest
              """
            }
          }
        }
      }
    }

    stage('Deploy Changed Services to AWS EC2') {
//       when {
//         expression { env.CHANGED_SERVICES != "" }
//       }
      steps {
        sshagent(credentials: ["deploy-key"]) {
          sh """
          scp -o StrictHostKeyChecking=no docker-compose.yml ubuntu@${deployHost}:/home/ubuntu/docker-compose.yml

          ssh -o StrictHostKeyChecking=no ubuntu@${deployHost} '
          cd /home/ubuntu && \
          aws ecr get-login-password --region ${REGION} | docker login --username AWS --password-stdin ${ECR_URL} && \
          docker-compose pull ${env.CHANGED_SERVICES} && \
          docker-compose up -d ${env.CHANGED_SERVICES}'
          """
        }
      }
    }

  }
}
