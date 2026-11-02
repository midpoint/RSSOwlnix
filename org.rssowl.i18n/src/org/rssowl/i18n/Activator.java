/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2009 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License v1.0 which accompanies this    **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl Development Team - initial API and implementation             **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.i18n;

import org.eclipse.osgi.service.localization.BundleLocalization;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.service.FindHook;

import java.util.Hashtable;

/**
 * Registers the external localization service very early, before the workbench
 * reads the labels from <code>plugin.xml</code>.
 * <p>
 * The bundle is started at start level 1 from <code>config.ini</code>, before
 * the extension registry and the workbench. It must not depend on
 * <code>org.rssowl.core</code> so that it can start at this early stage.
 * </p>
 *
 * @author RSSOwl Development Team
 */
public class Activator implements BundleActivator {
  private ServiceRegistration<?> fRegistration;
  private ServiceRegistration<?> fHookRegistration;

  /*
   * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
   */
  @Override
  public void start(BundleContext context) throws Exception {
    Hashtable<String, Object> properties = new Hashtable<>();
    properties.put(Constants.SERVICE_RANKING, Integer.MAX_VALUE);
    fRegistration = context.registerService(BundleLocalization.class.getName(), new ExternalBundleLocalization(), properties);

    /* The framework's own localization service has the same ranking and a lower service id. */
    fHookRegistration = context.registerService(FindHook.class.getName(), new ExternalLocalizationHook(), null);

    if (System.getProperty("rssowlnix.debugLanguage") != null) //$NON-NLS-1$
      System.err.println("RSSOwlnix i18n: registered early BundleLocalization service"); //$NON-NLS-1$
  }

  /*
   * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
   */
  @Override
  public void stop(BundleContext context) throws Exception {
    if (fHookRegistration != null) {
      fHookRegistration.unregister();
      fHookRegistration = null;
    }
    if (fRegistration != null) {
      fRegistration.unregister();
      fRegistration = null;
    }
  }
}
