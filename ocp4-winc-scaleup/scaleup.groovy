pipeline {
    agent {
        label "${params.JENKINS_SLAVE_LABEL}"
    }

    environment {
        KUBECONFIG_URL="https://openshift-qe-jenkins.rhev-ci-vms.eng.rdu2.redhat.com/job/Launch%20Environment%20Flexy/${env.FLEXY_BUILD_NUMBER}/artifact/workdir/install-dir/auth/kubeconfig"
        WNI_URL="https://github.com/openshift/windows-machine-config-bootstrapper/releases/download/v4.4.2-alpha/wni"
    }

    stages {
        stage('Set job name to triggered user') {
            steps {
                script {
                    def userCause = currentBuild.rawBuild.getCause(hudson.model.Cause$UserIdCause)
                    def userId = userCause?.userId
                    if (userId) {
                        currentBuild.rawBuild.setDisplayName(userId + "-" + env.FLEXY_BUILD_NUMBER)
                    }
                }
            }
        }

        // TODO: change to terraform in future, may need wjiang's help
        stage('Prepare winc nodes') {
            steps {
                script {
                    ansiColor('gnome-terminal') {
                      withCredentials([
                        file(credentialsId: 'b73d6ed3-99ff-4e06-b2d8-64eaaf69d1db', variable: 'AWS_CREDS'),
                        ]) {
                        sh """
                        wget ${KUBECONFIG_URL} --no-check-certificate
                        # wget ${WNI_URL} --quiet
                        # chmod 777 wni
                        # ./wni aws create --kubeconfig kubeconfig --credentials ${AWS_CREDS} --credential-account default --instance-type m5a.large --ssh-key openshift-qe --private-key ~/.ssh/openshift-qe.pem
                        env.WINC_WORKERS="worker-test,use-test,pass-test"
                        """
                      }
                    }
                }
            }
        }

        stage ('Run scale-up job') {
            steps {
                script {
                    // def rhelProps = readJSON file: windows-node-installer.json
                    build job: 'ocp4-winc-scaleup-runner', parameters: [
                        string(name: 'OPERATION', value: "SCALEUP"),
                        string(name: 'WINC_WORKERS', value: "${env.WINC_WORKERS}"),
                        string(name: 'KUBECONFIG_URL', value: "${env.KUBECONFIG_URL}"),
                        string(name: 'OCP_VERSION', value: "4.4"),
                        [$class: 'LabelParameterValue', name: 'JENKINS_SLAVE_LABEL', label: "${params.JENKINS_SLAVE_LABEL}"],
                    ], wait: true
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'windows-node-installer.json', fingerprint: true
            cleanWs()
        }
    }
}