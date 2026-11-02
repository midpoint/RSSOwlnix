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
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.hooks.service.FindHook;

import java.util.Collection;

/**
 * Hides the default <code>BundleLocalization</code> service of the OSGi
 * framework. Both services use the maximum ranking, and in that case the
 * framework service (which has the lower service id) would always win. The
 * external localization service of this bundle is registered instead.
 *
 * @author RSSOwl Development Team
 */
public class ExternalLocalizationHook implements FindHook {

  /*
   * @see org.osgi.framework.hooks.service.FindHook#find(org.osgi.framework.BundleContext, java.lang.String, java.lang.String, boolean, java.util.Collection)
   */
  @Override
  public void find(BundleContext context, String name, String filter, boolean allServices, Collection<ServiceReference<?>> references) {
    if (!BundleLocalization.class.getName().equals(name))
      return;

    references.removeIf(reference -> reference.getBundle() != null && reference.getBundle().getBundleId() == 0L);
  }
}
