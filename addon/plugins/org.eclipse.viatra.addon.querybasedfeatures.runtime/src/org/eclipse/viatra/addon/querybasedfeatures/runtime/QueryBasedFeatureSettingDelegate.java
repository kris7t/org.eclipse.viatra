/*******************************************************************************
 * Copyright (c) 2010-2013, Abel Hegedus, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Abel Hegedus - initial API and implementation
 *******************************************************************************/
package org.eclipse.viatra.addon.querybasedfeatures.runtime;

import static com.google.common.base.Preconditions.checkArgument;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import org.eclipse.emf.common.notify.Notifier;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.InternalEObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.util.BasicSettingDelegate;
import org.eclipse.viatra.query.runtime.api.AdvancedViatraQueryEngine;
import org.eclipse.viatra.query.runtime.api.IPatternMatch;
import org.eclipse.viatra.query.runtime.api.IQuerySpecification;
import org.eclipse.viatra.query.runtime.api.ViatraQueryMatcher;
import org.eclipse.viatra.query.runtime.exception.ViatraQueryException;
import org.eclipse.viatra.query.runtime.matchers.psystem.annotations.PAnnotation;
import org.eclipse.viatra.query.runtime.util.ViatraQueryLoggingUtil;

import com.google.common.collect.Lists;

/**
 * 
 * @author Abel Hegedus
 *
 */
public class QueryBasedFeatureSettingDelegate extends BasicSettingDelegate.Stateless {

    /**
     * Weak hash map for keeping the created objects for each notifier
     */
    private final Map<AdvancedViatraQueryEngine,WeakReference<QueryBasedFeature>> queryBasedFeatures = new WeakHashMap<AdvancedViatraQueryEngine, WeakReference<QueryBasedFeature>>();

    private final IQuerySpecification<? extends ViatraQueryMatcher<? extends IPatternMatch>> querySpecification;

    private final QueryBasedFeatureSettingDelegateFactory delegateFactory;

    private final boolean dynamicEMFMode;
    
    private boolean isResourceScope;

    private QueryBasedFeatureParameters parameters;
    
    /**
     * Constructs a new {@link QueryBasedFeatureSettingDelegate} instance based on the given parameters.
     * The scope of the VIATRA Query engine in this case will be the one provided by {@link QueryBasedFeatureHelper.prepareNotifierForSource({@link InternalEObject})}.
     *  
     * @param eStructuralFeature the parent structural feature of the setting delegate
     * @param factory the factory used to create VIATRA Query engine for the setting delegate
     * @param querySpecification the query specification used for the evaluation of the setting delegate
     * @param dynamicEMFMode indicates whether the engine should be created in dynamic EMF mode
     */
    public <Match extends IPatternMatch, Matcher extends ViatraQueryMatcher<Match>> QueryBasedFeatureSettingDelegate(EStructuralFeature eStructuralFeature,
            QueryBasedFeatureSettingDelegateFactory factory,
            IQuerySpecification<Matcher> querySpecification, boolean dynamicEMFMode) {
        this(eStructuralFeature, factory, querySpecification, false, dynamicEMFMode);
    }
    
    /**
     * Constructs a new {@link QueryBasedFeatureSettingDelegate} instance based on the given parameters.
     * 
     * @param eStructuralFeature the parent structural feature of the setting delegate
     * @param factory the factory used to create VIATRA Query engine for the setting delegate
     * @param querySpecification the query specification used for the evaluation of the setting delegate
     * @param isResourceScope indicates whether the {@link Resource} of the {@link InternalEObject} is enough as a scope during the evaluation of the setting delegate 
     * @param dynamicEMFMode indicates whether the engine should be created in dynamic EMF mode
     */
    public <Match extends IPatternMatch, Matcher extends ViatraQueryMatcher<Match>> QueryBasedFeatureSettingDelegate(EStructuralFeature eStructuralFeature,
            QueryBasedFeatureSettingDelegateFactory factory,
            IQuerySpecification<Matcher> querySpecification, 
            boolean isResourceScope, boolean dynamicEMFMode) {
        super(eStructuralFeature);
        this.delegateFactory = factory;
        this.querySpecification = querySpecification;
        this.dynamicEMFMode = dynamicEMFMode;
        this.isResourceScope = isResourceScope;
        
        parameters = new QueryBasedFeatureParameters(querySpecification);

        List<PAnnotation> qbfAnnotations = querySpecification.getAnnotationsByName("QueryBasedFeature");
        if(qbfAnnotations.isEmpty()) {
            // called probably by Xcore, use defaults
        } else if(qbfAnnotations.size() == 1) {
           PAnnotation annotation = qbfAnnotations.iterator().next();
           processQBFAnnotation(annotation);
        } else {
            // at least one of them has to specify this feature
            for (PAnnotation annotation : qbfAnnotations) {
                Object featureParam = annotation.getFirstValue("feature");
                if (featureParam instanceof String && eStructuralFeature.getName().equals(featureParam)) {
                    processQBFAnnotation(annotation);
                }
            }
        }
    }

    private void processQBFAnnotation(PAnnotation annotation) {
        Object sourceParam = annotation.getFirstValue("source");
        if (sourceParam instanceof String) {
            parameters.sourceVar = (String) sourceParam;
        }
        Object targetParam = annotation.getFirstValue("target");
        if (targetParam instanceof String) {
            parameters.targetVar = (String) targetParam;
        }
        Object keepCacheParam = annotation.getFirstValue("keepCache");
        if (keepCacheParam instanceof Boolean) {
            parameters.keepCache = (Boolean) keepCacheParam;
        }
        Object kindParam = annotation.getFirstValue("kind");
        if (kindParam instanceof String) {
            parameters.kind = QueryBasedFeatureKind.parseKindString((String) kindParam);
        }
    }

    @Override
    protected Object get(InternalEObject owner, boolean resolve, boolean coreType) {
        
        // TODO this can be expensive to do
        Notifier notifierForSource = null;
        if (isResourceScope) {
            notifierForSource = owner.eResource();
        }
        if (notifierForSource == null) {
            notifierForSource = QueryBasedFeatureHelper.prepareNotifierForSource(owner);    
        }
                
        QueryBasedFeature queryBasedFeature = initializeSettingDelegateInternal(notifierForSource);

        return queryBasedFeature.getValue(owner);
    }
    
    /**
     * 
     * Initializes the query based feature setting delegate using the given notifier as the root of the query engine
     * base index. This is usually the {@link ResourceSet} unless you know what you are doing.
     * 
     * @param rootNotifier
     *            the root of the indexing for the matcher driving the feature
     * @since 1.3
     */
    public void initializeSettingDelegate(Notifier rootNotifier) {
        checkArgument(rootNotifier != null, "Notifier cannot be null");
        initializeSettingDelegateInternal(rootNotifier);
    }

    private QueryBasedFeature initializeSettingDelegateInternal(Notifier notifierForSource) {
        AdvancedViatraQueryEngine engine = null;
        try {
            engine = delegateFactory.getEngineForNotifier(notifierForSource, dynamicEMFMode);
        } catch (ViatraQueryException e) {
            ViatraQueryLoggingUtil.getLogger(getClass()).error("Engine preparation failed", e);
            throw new IllegalStateException("Engine preparation failed", e);
        }
        
        WeakReference<QueryBasedFeature> weakReference = queryBasedFeatures.get(engine);
        QueryBasedFeature queryBasedFeature = weakReference == null ? null : weakReference.get();
        if(queryBasedFeature == null) {
            queryBasedFeature = QueryBasedFeatureHelper.createQueryBasedFeature(eStructuralFeature, parameters.kind, parameters.keepCache);
            if(queryBasedFeature != null) {
                queryBasedFeatures.put(engine, new WeakReference<QueryBasedFeature>(queryBasedFeature));
            }
        }
        
        if (queryBasedFeature != null && !queryBasedFeature.isInitialized()) {
            initializeQueryBasedFeature(engine, queryBasedFeature);
        }
        return queryBasedFeature;
    }

	private void initializeQueryBasedFeature(AdvancedViatraQueryEngine engine, QueryBasedFeature queryBasedFeature) {
		try {
			List<QueryBasedFeature> delayedFeatures = delegateFactory.getDelayedFeatures().get(engine);
			// query-based feature initialization is delayed, but list is used as ordered set
			if(!delayedFeatures.contains(queryBasedFeature)){
				delayedFeatures.add(queryBasedFeature);
				@SuppressWarnings("unchecked")
				ViatraQueryMatcher<IPatternMatch> matcher = (ViatraQueryMatcher<IPatternMatch>) this.querySpecification
				.getMatcher(engine);
				if (!queryBasedFeature.isInitialized()) {
					queryBasedFeature.setMatcher(matcher);
					queryBasedFeature.setSourceParamName(parameters.sourceVar);
					queryBasedFeature.setTargetParamName(parameters.targetVar);
					// the first feature in the list can initialize the rest
					Iterator<QueryBasedFeature> iterator = delayedFeatures.iterator();
					if(iterator.hasNext() && iterator.next().equals(queryBasedFeature)){
						initializeDelayedFeature(queryBasedFeature, delayedFeatures);
						// delayed query-based features are initialized 
						ArrayList<QueryBasedFeature> delayedFeatureList = Lists.newArrayList(delayedFeatures);
						for (QueryBasedFeature delayedFeature : delayedFeatureList) {
							initializeDelayedFeature(delayedFeature, delayedFeatures);
						}
					}
				}
			}
		} catch (ViatraQueryException e) {
		    ViatraQueryLoggingUtil.getLogger(getClass()).error("Handler initialization failed", e);
		}
	}

	private void initializeDelayedFeature(QueryBasedFeature queryBasedFeature, List<QueryBasedFeature> delayedFeatures) {
		queryBasedFeature.initialize(queryBasedFeature.getMatcher(), queryBasedFeature.getSourceParamName(), queryBasedFeature.getTargetParamName());
		queryBasedFeature.startMonitoring();
		delayedFeatures.remove(queryBasedFeature);
	}

    @Override
    protected boolean isSet(InternalEObject owner) {
        return false;
    }
    
    private class QueryBasedFeatureParameters{
        
        public String sourceVar;
        public String targetVar;
        
        public QueryBasedFeatureKind kind;
        public boolean keepCache;

        /**
         * @param querySpecification
         */
        public QueryBasedFeatureParameters(IQuerySpecification<? extends ViatraQueryMatcher<? extends IPatternMatch>> querySpecification) {
            List<String> parameterNames = querySpecification.getParameterNames();
            sourceVar = parameterNames.get(0);
            targetVar = parameterNames.get(1);
            keepCache = true;
            if(eStructuralFeature.isMany()) {
                kind = QueryBasedFeatureKind.MANY_REFERENCE;
            } else {
                kind = QueryBasedFeatureKind.SINGLE_REFERENCE;
            }
        }
      }

}
