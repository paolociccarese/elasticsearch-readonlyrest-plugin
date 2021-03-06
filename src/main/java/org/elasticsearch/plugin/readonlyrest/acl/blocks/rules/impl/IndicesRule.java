package org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.impl;

import com.google.common.collect.Lists;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.plugin.readonlyrest.ConfigurationHelper;
import org.elasticsearch.plugin.readonlyrest.SecurityPermissionException;
import org.elasticsearch.plugin.readonlyrest.acl.RequestContext;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.Rule;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.RuleExitResult;
import org.elasticsearch.plugin.readonlyrest.acl.blocks.rules.RuleNotConfiguredException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;

/**
 * Created by sscarduzio on 20/02/2016.
 */
public class IndicesRule extends Rule {

  private final static ESLogger logger = Loggers.getLogger(IndicesRule.class);

  private List<String> indicesToMatch;

  public IndicesRule(Settings s) throws RuleNotConfiguredException {
    super(s);
    String[] a = s.getAsArray(KEY);
    if (a != null && a.length > 0) {
      indicesToMatch = Lists.newArrayList();
      for (int i = 0; i < a.length; i++) {
        if (!ConfigurationHelper.isNullOrEmpty(a[i])) {
          indicesToMatch.add(a[i].trim());
        }
      }
    } else {
      throw new RuleNotConfiguredException();
    }
  }


  public static String[] getIndices(final ActionRequest ar) {
    final String[][] out = {new String[1]};
    AccessController.doPrivileged(
        new PrivilegedAction<Void>() {
          @Override
          public Void run() {
            String[] indices;
            try {
              Method m = ar.getClass().getMethod("indices");
              m.setAccessible(true);
              indices = (String[]) m.invoke(ar);
            } catch ( SecurityException | IllegalArgumentException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new SecurityPermissionException("Insufficient permissions to extract the indices. Abort! Cause: " + e.getMessage(), e);
            }
            out[0] = indices;
            return null;
          }
        }
    );

    return out[0];
  }

  @Override
  public RuleExitResult match(RequestContext rc) {
    String[] indices =  getIndices(rc.getActionRequest());
    if(indices == null || indices.length == 0) {
      logger.warn("didn't find any index for this request: " + rc.getRequest().method() + " " + rc.getRequest().rawPath());
      return NO_MATCH;
    }
    for(String requiredIndex: indices){
      if(!indicesToMatch.contains(requiredIndex)){
        logger.debug("This request uses the index '"+ requiredIndex + "' which is not in the list.");
        return NO_MATCH;
      }
    }
    return MATCH;
  }
}
