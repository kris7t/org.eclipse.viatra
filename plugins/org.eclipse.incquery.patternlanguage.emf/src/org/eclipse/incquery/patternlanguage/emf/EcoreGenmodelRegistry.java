/*******************************************************************************
 * Copyright (c) 2010-2012, Zoltan Ujhelyi, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Zoltan Ujhelyi - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.patternlanguage.emf;

import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.common.util.TreeIterator;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class EcoreGenmodelRegistry {

    private Map<String, String> genmodelUriMap;
    private Map<String, GenPackage> genpackageMap = Maps.newHashMap();
    @Inject
    private Logger logger;
    private Set<String> reportedProblematicGenmodelUris = Sets.newHashSet();
    @Inject(optional=true)
    private IGenmodelMappingLoader loader;
    
    private void ensureInitialized() {
        if (genmodelUriMap == null && loader != null) {
            genmodelUriMap = loader.loadGenmodels();
        }
    }
    
    public GenPackage findGenPackage(String nsURI, ResourceSet set) {
        ensureInitialized();
        if (!genpackageMap.containsKey(nsURI)) {
            if (!genmodelUriMap.containsKey(nsURI)) {
                return null;
            }
            GenPackage genPackage = loadGenPackage(nsURI, genmodelUriMap.get(nsURI), set);
            if (genPackage != null) {
                genpackageMap.put(nsURI, genPackage);
            }
            return genPackage;
        }
        return genpackageMap.get(nsURI);
    }

    private GenPackage loadGenPackage(String nsURI, String genmodelUri, ResourceSet set) {
        try {
            URI uri = URI.createURI(genmodelUri);
            if (uri.isRelative()) {
                uri = URI.createPlatformPluginURI(genmodelUri, true);
            }
            Resource resource = set.getResource(uri, true);
            TreeIterator<EObject> it = resource.getAllContents();
            while (it.hasNext()) {
                EObject object = it.next();
                if (object instanceof GenPackage) {
                    if (((GenPackage) object).getNSURI().equals(nsURI)) {
                        return (GenPackage) object;
                    } else if (object instanceof GenModel) {
                        it.prune();
                    }
                }
            }
        } catch (RuntimeException ex) {
            if (!reportedProblematicGenmodelUris.contains(genmodelUri)) {
                reportedProblematicGenmodelUris.add(genmodelUri);
                logger.error("Error while retrieving genmodel of EPackage " + nsURI + " from location: " + genmodelUri, ex);
            }
        }
        return null;
    }
}