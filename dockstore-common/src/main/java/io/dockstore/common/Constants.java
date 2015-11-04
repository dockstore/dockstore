/*
 * Copyright (C) 2015 Collaboratory
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.dockstore.common;

/**
 * 
 * This describes all keys available in Dockstore config files.
 *
 * @author xliu
 */
public class Constants {
    public static final String WEBSERVICE_BASE_PATH = "webservice.base_path";
    public static final String WEBSERVICE_TOKEN = "webservice.token";
    public static final String WEBSERVICE_EXTRA_FILES = "webservice.extra_files";

    public static final String POSTGRES_HOST = "database.postgresHost";
    public static final String POSTGRES_USERNAME = "database.postgresUser";
    public static final String POSTGRES_PASSWORD = "database.postgresPass";
    public static final String POSTGRES_DBNAME = "database.postgresDBName";
    public static final String POSTGRES_MAX_CONNECTIONS = "database.maxConnections";

    public static final String RABBIT_HOST = "rabbit.rabbitMQHost";
    public static final String RABBIT_USERNAME = "rabbit.rabbitMQUser";
    public static final String RABBIT_PASSWORD = "rabbit.rabbitMQPass";
    public static final String RABBIT_QUEUE_NAME = "rabbit.rabbitMQQueueName";

    public static final String PROVISION_MAX_RUNNING_CONTAINERS = "provision.max_running_containers";
    public static final String PROVISION_REAP_FAILED_WORKERS = "provision.reap_failed_workers";
    public static final String PROVISION_YOUXIA_DEPLOYER = "provision.youxia_deployer_parameters";
    public static final String PROVISION_YOUXIA_REAPER = "provision.youxia_reaper_parameters";

    public static final String COORDINATOR_SECONDS_BEFORE_LOST = "coordinator.max_seconds_before_lost";

    public static final String JOB_GENERATOR_CHECK_JOB_HASH = "generator.check_previous_job_hash";
    public static final String JOB_GENERATOR_FILTER_KEYS_IN_HASH = "generator.job_filter_hash_keys";

    public static final String WORKER_POSTWORKER_SLEEP = "worker.postworkerSleep";
    public static final String WORKER_PREWORKER_SLEEP = "worker.preworkerSleep";
    public static final String WORKER_HEARTBEAT_RATE = "worker.heartbeatRate";
    public static final String WORKER_HOST_USER_NAME = "worker.hostUserName";
    public static final String WORKER_ENDLESS = "worker.endless";
    public static final String WORKER_MAX_RUNS = "worker.max-runs";
    public static final String WORKER_SEQWARE_ENGINE = "worker.seqware-engine";
    public static final String WORKER_SEQWARE_SETTINGS_FILE = "worker.seqware-settings-file";

    public static final String REPORT_NAMESPACE = "report.namespace";
    public static final String REPORT_TOKEN = "report.slack_token";
    public static final String SEQWARE_WHITESTAR_ENGINE = "whitestar";
}
