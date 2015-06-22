/**
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package c3.ops.priam.cli;

import c3.ops.priam.IConfiguration;
import c3.ops.priam.ICredential;
import c3.ops.priam.aws.S3BackupPath;
import c3.ops.priam.aws.S3FileSystem;
import c3.ops.priam.aws.SDBInstanceFactory;
import c3.ops.priam.backup.AbstractBackupPath;
import c3.ops.priam.backup.IBackupFileSystem;
import c3.ops.priam.compress.ICompression;
import c3.ops.priam.compress.SnappyCompression;
import c3.ops.priam.defaultimpl.ClearCredential;
import c3.ops.priam.defaultimpl.PriamConfiguration;
import c3.ops.priam.identity.IMembership;
import c3.ops.priam.identity.IPriamInstanceFactory;
import c3.ops.priam.identity.token.*;
import c3.ops.priam.utils.ITokenManager;
import c3.ops.priam.utils.Sleeper;
import c3.ops.priam.utils.ThreadSleeper;
import c3.ops.priam.utils.TokenManager;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

class LightGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(IConfiguration.class).to(PriamConfiguration.class).asEagerSingleton();
    bind(IPriamInstanceFactory.class).to(SDBInstanceFactory.class);
    bind(AbstractBackupPath.class).to(S3BackupPath.class);
    bind(ICompression.class).to(SnappyCompression.class);
    bind(Sleeper.class).to(ThreadSleeper.class);
    bind(ITokenManager.class).to(TokenManager.class);
    bind(IBackupFileSystem.class).annotatedWith(Names.named("backup")).to(S3FileSystem.class);
    bind(IBackupFileSystem.class).annotatedWith(Names.named("incr_restore")).to(S3FileSystem.class);
    bind(IBackupFileSystem.class).annotatedWith(Names.named("backup_status")).to(S3FileSystem.class);
    bind(ICredential.class).to(ClearCredential.class);
    //bind(ICredential.class).to(IAMCredential.class);
    bind(IMembership.class).to(StaticMembership.class);
    bind(IDeadTokenRetriever.class).to(DeadTokenRetriever.class);
    bind(IPreGeneratedTokenRetriever.class).to(PreGeneratedTokenRetriever.class);
    bind(INewTokenRetriever.class).to(NewTokenRetriever.class);

  }
}

