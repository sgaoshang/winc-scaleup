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
                        export KUBECONFIG=kubeconfig
                        hybrid_query=`oc get network.operator cluster -o jsonpath='{.spec.defaultNetwork.ovnKubernetesConfig.hybridOverlayConfig.hybridClusterNetwork}'`
                        # echo \$hybrid_query
                        if [ "\$hybrid_query" == "" ]; then
                            oc patch network.operator cluster --type=merge -p '{"spec":{"defaultNetwork":{"ovnKubernetesConfig":{"hybridOverlayConfig":{"hybridClusterNetwork":[{"cidr":"10.132.0.0/14","hostPrefix":23}]}}}}}'
                            # Wait until openshift-ovn-kubernetes pod ready
                            loop_counter=0
                            while [ \$loop_counter -le 10 ]
                            do
                                status=`oc get pod -n openshift-ovn-kubernetes --field-selector=status.phase!=Running`
                                if [ X\$status == X"" ]; then
                                    break
                                fi
                                echo "Waiting for openshift-ovn-kubernetes pod ready: $n times."
                                sleep 10
                                loop_counter=\$(( loop_counter+1 ))
                            done
                        fi
                        wget ${WNI_URL} --quiet
                        chmod 777 wni
                        wni_putput=`./wni aws create --kubeconfig kubeconfig --credentials ${AWS_CREDS} --credential-account default --instance-type m5a.large --ssh-key openshift-qe --private-key ~/.ssh/openshift-qe.pem`
                        echo \$wni_putput | grep -Po "(?<=Windows instance created at ).*(?= using )|(?<= using ).*(?= as user and )|(?<= as user and ).*(?= password)" | tr "\n" "," > winc_workers.txt
                        """
                      }
                    }
                }
            }
        }

        stage ('Run scale-up job') {
            steps {
                script {
                    WINC_WORKERS =  readFile('winc_workers.txt').trim()
                    build job: 'ocp4-winc-scaleup-runner', parameters: [
                        string(name: 'OPERATION', value: "SCALEUP"),
                        string(name: 'WINC_WORKERS', value: "${WINC_WORKERS}"),
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
            archiveArtifacts artifacts: 'kubeconfig, windows-node-installer.json, winc_workers.txt', fingerprint: true
            cleanWs()
        }
    }
}