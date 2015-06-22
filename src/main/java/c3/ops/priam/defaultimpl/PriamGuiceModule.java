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
package c3.ops.priam.defaultimpl;

import c3.ops.priam.ICredential;
import c3.ops.priam.aws.S3FileSystem;
import c3.ops.priam.backup.IBackupFileSystem;
import c3.ops.priam.identity.token.*;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import org.quartz.SchedulerFactory;
import org.quartz.impl.StdSchedulerFactory;


public class PriamGuiceModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(SchedulerFactory.class).to(StdSchedulerFactory.class).asEagerSingleton();

    bind(IBackupFileSystem.class).annotatedWith(Names.named("backup")).to(S3FileSystem.class);
    bind(IBackupFileSystem.class).annotatedWith(Names.named("incr_restore")).to(S3FileSystem.class);
    bind(IBackupFileSystem.class).annotatedWith(Names.named("backup_status")).to(S3FileSystem.class);
    bind(ICredential.class).to(ClearCredential.class);
    //bind(ICredential.class).to(IAMCredential.class);
    bind(IDeadTokenRetriever.class).to(DeadTokenRetriever.class);
    bind(IPreGeneratedTokenRetriever.class).to(PreGeneratedTokenRetriever.class);
    bind(INewTokenRetriever.class).to(NewTokenRetriever.class);
  }
}
