package hello.system;

import android.accounts.Account;
import android.accounts.AccountManager;
import java.util.Arrays;

/** AccountManager.removeAccount asks the authenticator (getAccountRemovalAllowed): it refuses "kept" and allows "removable". */
final class AccountRemovalCheck implements Check {
    @Override public String run(android.content.Context context) throws Exception {
        AccountManager am = AccountManager.get(context);
        Account removable = new Account("removable", "hello.system.account"), kept = new Account("kept", "hello.system.account");
        am.addAccountExplicitly(removable, null, null);
        am.addAccountExplicitly(kept, null, null);
        try {
            boolean refused = am.removeAccount(kept, null, null, null).getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT);
            Check.require(!refused && Arrays.asList(am.getAccountsByType(kept.type)).contains(kept), "the authenticator's refusal was ignored");
            boolean allowed = am.removeAccount(removable, null, null, null).getResult().getBoolean(AccountManager.KEY_BOOLEAN_RESULT);
            Check.require(allowed && !Arrays.asList(am.getAccountsByType(removable.type)).contains(removable), "an allowed removal did not remove it");
            return "refused kept, removed removable";
        } finally { am.removeAccountExplicitly(kept); am.removeAccountExplicitly(removable); }
    }
}
