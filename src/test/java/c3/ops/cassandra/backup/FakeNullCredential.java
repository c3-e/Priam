package c3.ops.cassandra.backup;

import c3.ops.cassandra.ICredential;
import com.amazonaws.auth.AWSCredentialsProvider;

public class FakeNullCredential implements ICredential {
	public AWSCredentialsProvider getAwsCredentialProvider() {
		// TODO Auto-generated method stub
		return null;
	}
}
