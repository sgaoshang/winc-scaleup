pipeline {
    agent {
        label "${params.JENKINS_SLAVE_LABEL}"
    }

    stages {
        stage('set job name to triggered user') {
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

        // stage('Clone the repo') {
        //     steps {
        //         checkout changelog: false,
        //             poll: false,
        //             scm: [
        //                 $class: 'GitSCM', 
        //                 branches: [[name: "master"]],
        //                 doGenerateSubmoduleConfigurations: false,
        //                 extensions: [
        //                     [$class: 'CloneOption', noTags: true, reference: '', shallow: true],
        //                     [$class: 'PruneStaleBranch'],
        //                     [$class: 'CleanCheckout'],
        //                     [$class: 'IgnoreNotifyCommit'],
        //             ],
        //             submoduleCfg: [],
        //             userRemoteConfigs: [[
        //               credentialsId: 'c9fb86e4-bd29-425f-a834-16ef21009d84',
        //               name: 'origin',
        //               refspec: "+refs/heads/master:refs/remotes/origin/master",
        //               url: 'ssh://openshift-jenkins@code.engineering.redhat.com:22/openshift-misc'
        //               ]]
        //             ]
        //     }
        // }

        // TODO: IPI only now
        stage('Fetch artifacts from upstream job') {
            environment {
                BUILD_NUMBER = "${env.FLEXY_BUILD_NUMBER}"
            }
            steps {
                copyArtifacts filter: 'workdir/kubeconfig',
                              fingerprintArtifacts: true,
                              flatten: true,
                              projectName: 'Launch Environment Flexy',
                              selector: specific(env.BUILD_NUMBER)
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
                        ./wni aws create --kubeconfig kubeconfig --credentials ${AWS_CREDS} --credential-account default --instance-type m5a.large --ssh-key openshift-qe --private-key ~/.ssh/openshift-qe.pem
                        """
                      }
                    }
                }
            }
        }

        stage ('Run scale-up job') {
            // environment {
            //     CLUSTER_KUBECONFIG_LOCATION = "${readYaml(file: "cluster_info.yaml")["INSTALLER"]["KUBECONFIG_URL"]}"
            //     PLATFORM = "${readYaml(file: "cluster_info.yaml")["GENERAL"]["PLATFORM"]}"
            //     V_X = "${readYaml(file: "cluster_info.yaml")["INSTALLER"]["VER_X"]}"
            //     V_Y = "${readYaml(file: "cluster_info.yaml")["INSTALLER"]["VER_Y"]}"
            //     NETWORK_TYPE = "${readYaml(file: "cluster_info.yaml")["GENERAL"]["NETWORK_TYPE"]}"
            //     BASTION = "${readYaml(file: "cluster_info.yaml")["INSTALLER"]["BASTION"]}"
            //     FLEXY_VARS = "${readYaml(file: "cluster_info.yaml")["TEMPLATE_VARS"]}"
            //     scaleup_vars = "${env.EXTRA_VARS}\nplaceholder: true"
            // }
            steps {
                script {
                    def rhelProps = readJSON file: env.OUTPUT_PATH
                    // env.RHEL_WORKER_LIST = rhelProps.RHEL_WORKER_LIST
                    def runner_vars = ""
                    runner_vars += "ssh_user: ${rhelProps.ANSIBLE_USER.value}\n"
                    runner_vars += "platform: ${env.PLATFORM}\n"
                    runner_vars += "network_type: ${env.NETWORK_TYPE}\n"
                    runner_vars += "bastion: ${env.BASTION}"
                    build job: 'ocp4-rhel-scaleup-runner', parameters: [
                        string(name: 'OPERATION', value: "SCALEUP"),
                        string(name: 'RHEL_WORKERS', value: "${rhelProps.RHEL_WORKER_LIST.value}"),
                        string(name: 'KUBECONFIG_URL', value: "${env.CLUSTER_KUBECONFIG_LOCATION}"),
                        string(name: 'OCP_VERSION', value: "${env.V_X}.${env.V_Y}"),
			[$class: 'LabelParameterValue', name: 'JENKINS_SLAVE_LABEL', label: "${params.JENKINS_SLAVE_LABEL}"],
                        text(name: 'EXTRA_VARS', value: """
${env.FLEXY_VARS}
${env.scaleup_vars}
${runner_vars}
""")
                    ], wait: true
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: 'terraform.tfstate.d/**/*.*', fingerprint: true
            cleanWs()
        }
    }
}
