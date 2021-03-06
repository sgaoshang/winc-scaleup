pipeline {
    agent {
        label "sgao-winc"
    }

    environment {
        WNI_URL="https://github.com/openshift/windows-machine-config-bootstrapper/releases/download/v4.4.2-alpha/wni"
    }

    stages {
        stage('Set job name to triggered user') {
            steps {
                script {
                    def userCause = currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause)
                    def userId = userCause?.userId
                    if (userId) {
                        currentBuild.rawBuild.setDisplayName(userId + "-" + env.SCALEUP_BUILD_NUMBER)
                    }
                }
            }
        }

        stage('Fetch artifacts from upstream job') {
            environment {
                BUILD_NUMBER = "${env.SCALEUP_BUILD_NUMBER}"
            }
            steps {
                copyArtifacts fingerprintArtifacts: true,
                              flatten: true,
                              projectName: 'ocp4-winc-scaleup',
                              selector: specific(env.BUILD_NUMBER)
            }
        }

        stage('Destroy winc hosts') {
            steps {
                script {
                    ansiColor('gnome-terminal') {
                      withCredentials([
                        file(credentialsId: 'b73d6ed3-99ff-4e06-b2d8-64eaaf69d1db', variable: 'AWS_CREDS'),
                        ]) {
                        sh """
                        cat windows-node-installer.json
                        rm -rf wni; wget ${WNI_URL} --quiet; chmod 777 wni
                        ./wni aws destroy --kubeconfig kubeconfig --credentials ${AWS_CREDS} --credential-account default
                        """
                      }
                    }
                }
            }
        }
    }
}