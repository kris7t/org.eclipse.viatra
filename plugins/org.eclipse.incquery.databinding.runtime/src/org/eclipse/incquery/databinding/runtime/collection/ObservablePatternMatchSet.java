/*******************************************************************************
 * Copyright (c) 2010-2012, Abel Hegedus, Istvan Rath and Daniel Varro
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Abel Hegedus - initial API and implementation
 *******************************************************************************/
package org.eclipse.incquery.databinding.runtime.collection;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.databinding.observable.Diffs;
import org.eclipse.core.databinding.observable.Realm;
import org.eclipse.core.databinding.observable.set.AbstractObservableSet;
import org.eclipse.core.databinding.observable.set.SetDiff;
import org.eclipse.core.runtime.Assert;
import org.eclipse.incquery.databinding.runtime.api.IncQueryObservables;
import org.eclipse.incquery.runtime.api.IPatternMatch;
import org.eclipse.incquery.runtime.api.IQuerySpecification;
import org.eclipse.incquery.runtime.api.IncQueryEngine;
import org.eclipse.incquery.runtime.api.IncQueryMatcher;
import org.eclipse.incquery.runtime.evm.api.ExecutionSchema;
import org.eclipse.incquery.runtime.evm.api.RuleEngine;
import org.eclipse.incquery.runtime.evm.api.RuleSpecification;
import org.eclipse.incquery.runtime.evm.api.event.EventFilter;
import org.eclipse.incquery.runtime.evm.specific.Rules;
import org.eclipse.incquery.runtime.evm.specific.event.IncQueryFilterSemantics;
import org.eclipse.incquery.runtime.exception.IncQueryException;

import com.google.common.collect.Sets;

/**
 * Observable view of a match set for a given {@link IncQueryMatcher} on a model (match sets of an
 * {@link IncQueryMatcher} are not ordered by default).
 * 
 * <p>
 * This implementation uses the {@link ExecutionSchema} to get notifications for match set changes, and can be instantiated
 * using either an existing {@link IncQueryMatcher}, or an {@link IQuerySpecification} and {@link IncQueryEngine} or {@link RuleEngine}.
 * 
 * @author Abel Hegedus
 * 
 */
public class ObservablePatternMatchSet<Match extends IPatternMatch> extends AbstractObservableSet {

    private final Set<Match> cache = Collections.synchronizedSet(new HashSet<Match>());
    private final SetCollectionUpdate updater = new SetCollectionUpdate();
    private RuleSpecification<Match> specification;

    /**
     * Creates an observable view of the match set of the given {@link IQuerySpecification} initialized on the given
     * {@link IncQueryEngine}.
     * 
     * <p>
     * Consider using {@link IncQueryObservables#observeMatchesAsSet} instead!
     * 
     * @param querySpecification
     *            the {@link IQuerySpecification} used to create a matcher
     * @param engine
     *            the {@link IncQueryEngine} on which the matcher is created
     * @throws IncQueryException if the {@link IncQueryEngine} base index is not available
     */
    public <Matcher extends IncQueryMatcher<Match>> ObservablePatternMatchSet(IQuerySpecification<Matcher> querySpecification,
            IncQueryEngine engine) {
        this(querySpecification);
        ObservableCollectionHelper.prepareRuleEngine(engine, specification, null);
    }

    /**
     * Creates an observable view of the match set of the given {@link IQuerySpecification} initialized on the given
     * {@link IncQueryEngine}.
     * 
     * <p>
     * Consider using {@link IncQueryObservables#observeMatchesAsSet} instead!
     * 
     * @param querySpecification
     *            the {@link IQuerySpecification} used to create a matcher
     * @param engine
     *            the {@link IncQueryEngine} on which the matcher is created
     * @param filter the partial match to be used as filter
     * @throws IncQueryException if the {@link IncQueryEngine} base index is not available
     */
    public <Matcher extends IncQueryMatcher<Match>> ObservablePatternMatchSet(IQuerySpecification<Matcher> querySpecification,
            IncQueryEngine engine, Match filter) {
        this(querySpecification);
        ObservableCollectionHelper.prepareRuleEngine(engine, specification, filter);
    }

    public <Matcher extends IncQueryMatcher<Match>> ObservablePatternMatchSet(IQuerySpecification<Matcher> querySpecification,
            IncQueryEngine engine, Collection<Match> multifilters, IncQueryFilterSemantics semantics) {
        this(querySpecification);
        ObservableCollectionHelper.prepareRuleEngine(engine, specification, multifilters, semantics);
    }

    
    /**
     * Creates an observable view of the match set of the given {@link IQuerySpecification} initialized on the given
     * {@link IncQueryEngine}.
     * 
     * <p>
     * Consider using {@link IncQueryObservables#observeMatchesAsSet} instead!
     * 
     * @param querySpecification
     *            the {@link IQuerySpecification} used to create a matcher
     * @param engine
     *            an existing {@link ExecutionSchema} that specifies the used model
     */
    public <Matcher extends IncQueryMatcher<Match>> ObservablePatternMatchSet(IQuerySpecification<Matcher> querySpecification,
            RuleEngine engine) {
        this(querySpecification);
        engine.addRule(specification);
        ObservableCollectionHelper.fireActivations(engine, specification, specification.createEmptyFilter());
    }

    /**
     * Creates an observable view of the match set of the given {@link IQuerySpecification} initialized on the given
     * {@link IncQueryEngine}.
     * 
     * <p>
     * Consider using {@link IncQueryObservables#observeMatchesAsSet} instead!
     * 
     * @param querySpecification
     *            the {@link IQuerySpecification} used to create a matcher
     * @param engine
     *            an existing {@link ExecutionSchema} that specifies the used model
     * @param filter the partial match to be used as filter
     */
    public <Matcher extends IncQueryMatcher<Match>> ObservablePatternMatchSet(IQuerySpecification<Matcher> querySpecification,
            RuleEngine engine, Match filter) {
        this(querySpecification);
        EventFilter<Match> matchFilter = Rules.newSingleMatchFilter(filter);
		engine.addRule(specification, matchFilter);
		ObservableCollectionHelper.fireActivations(engine, specification, matchFilter);
    }

    public <Matcher extends IncQueryMatcher<Match>> ObservablePatternMatchSet(IQuerySpecification<Matcher> querySpecification,
            RuleEngine engine, Collection<Match> multifilters, IncQueryFilterSemantics semantics) {
        this(querySpecification);
        EventFilter<Match> matchFilter = Rules.newMultiMatchFilter(multifilters, semantics);
        engine.addRule(specification, matchFilter);
        ObservableCollectionHelper.fireActivations(engine, specification, matchFilter);
    }
    
    
    
    protected <Matcher extends IncQueryMatcher<Match>> ObservablePatternMatchSet(IQuerySpecification<Matcher> querySpecification) {
        super();
        this.specification = ObservableCollectionHelper.createRuleSpecification(updater, querySpecification);
    }
    
    /**
     * Creates an observable view of the match set of the given {@link IncQueryMatcher}.
     * 
     * <p>
     * Consider using {@link IncQueryObservables#observeMatchesAsSet} instead!
     * 
     * @param matcher
     *            the {@link IncQueryMatcher} to use as the source of the observable set
     */
    public <Matcher extends IncQueryMatcher<Match>> ObservablePatternMatchSet(Matcher matcher) {
        super();
        this.specification = ObservableCollectionHelper.createRuleSpecification(updater, matcher);
        ObservableCollectionHelper.prepareRuleEngine(matcher.getEngine(), specification, null);
    }
    
    @Override
    public Object getElementType() {
        return IPatternMatch.class;
    }

    @Override
    protected Set<Match> getWrappedSet() {
        return cache;
    }

    /**
     * @return the specification
     */
    public RuleSpecification<Match> getSpecification() {
        return specification;
    }

    public class SetCollectionUpdate implements IObservablePatternMatchCollectionUpdate<Match>{
        
        @SuppressWarnings("unchecked")
        @Override
        public void addMatch(Match match) {
            cache.add(match);
            final SetDiff diff = Diffs.createSetDiff(Sets.newHashSet(match), Collections.EMPTY_SET);
            Realm realm = getRealm();
            Assert.isNotNull(realm, "Data binding Realm must not be null");
			realm.exec(new Runnable() {

				@Override
				public void run() {
				    if (!isDisposed()) {
				        fireSetChange(diff);
				    }
				}
			});
        }
    
        @SuppressWarnings("unchecked")
        @Override
        public void removeMatch(Match match) {
            cache.remove(match);
            final SetDiff diff = Diffs.createSetDiff(Collections.EMPTY_SET, Sets.newHashSet(match));
            Realm realm = getRealm();
            Assert.isNotNull(realm, "Data binding Realm must not be null");
			realm.exec(new Runnable() {

				@Override
				public void run() {
				    if (!isDisposed()) {
				        fireSetChange(diff);
				    }
				}
			});
        }
    
    }

}