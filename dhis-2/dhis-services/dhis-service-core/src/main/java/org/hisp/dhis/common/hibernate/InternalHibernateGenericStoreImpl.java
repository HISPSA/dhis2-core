/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.common.hibernate;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.hibernate.SessionFactory;
import org.hisp.dhis.common.BaseIdentifiableObject;
import org.hisp.dhis.common.adapter.BaseIdentifiableObject_;
import org.hisp.dhis.common.adapter.Sharing_;
import org.hisp.dhis.dashboard.Dashboard;
import org.hisp.dhis.hibernate.HibernateGenericStore;
import org.hisp.dhis.hibernate.InternalHibernateGenericStore;
import org.hisp.dhis.hibernate.jsonb.type.JsonbFunctions;
import org.hisp.dhis.query.JpaQueryUtils;
import org.hisp.dhis.security.acl.AclService;
import org.hisp.dhis.user.CurrentUserDetails;
import org.hisp.dhis.user.CurrentUserGroupInfo;
import org.hisp.dhis.user.CurrentUserService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.CurrentUserUtil;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Disjunction;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.Subqueries;
import org.hibernate.Criteria;
import org.hibernate.SessionFactory;
import org.springframework.util.Assert;

/**
 * This class contains methods for generating predicates which are used for validating sharing
 * access permission.
 */
public class InternalHibernateGenericStoreImpl<T extends BaseIdentifiableObject>
        extends HibernateGenericStore<T> implements InternalHibernateGenericStore<T> {
  protected AclService aclService;

  protected final CurrentUserService currentUserService;

  public InternalHibernateGenericStoreImpl(
          SessionFactory sessionFactory,
          JdbcTemplate jdbcTemplate,
          ApplicationEventPublisher publisher,
          Class<T> clazz,
          AclService aclService,
          CurrentUserService currentUserService,
          boolean cacheable) {
    super(sessionFactory, jdbcTemplate, publisher, clazz, cacheable);

    checkNotNull(aclService);
    checkNotNull(currentUserService);
    this.aclService = aclService;
    this.currentUserService = currentUserService;
  }

  /**
   * Get Predicate for checking Sharing access for given User's uid and UserGroup Uids
   *
   * @param builder CriteriaBuilder
   * @param userUid User Uid for checking access
   * @param userGroupUids List of UserGroup Uid which given user belong to
   * @param access Access String for checking
   * @return List of {@link Predicate}
   */
  protected List<Function<Root<T>, Predicate>> getSharingPredicates(
          CriteriaBuilder builder, String userUid, Set<String> userGroupUids, String access) {
    List<Function<Root<T>, Predicate>> predicates = new ArrayList<>();

    Function<Root<T>, Predicate> userGroupPredicate =
            JpaQueryUtils.checkUserGroupsAccess(builder, userGroupUids, access);

    Function<Root<T>, Predicate> userPredicate =
            JpaQueryUtils.checkUserAccess(builder, userUid, access);

    predicates.add(
            root -> {
              Predicate disjunction =
                      builder.or(
                              builder.like(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.PUBLIC)),
                                      access),
                              builder.equal(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.PUBLIC)),
                                      "null"),
                              builder.isNull(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.PUBLIC))),
                              builder.isNull(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.OWNER))),
                              builder.equal(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.OWNER)),
                                      "null"),
                              builder.equal(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.OWNER)),
                                      userUid),
                              userPredicate.apply(root));

              Predicate ugPredicateWithRoot = userGroupPredicate.apply(root);

              if (ugPredicateWithRoot != null) {
                return builder.or(disjunction, ugPredicateWithRoot);
              }

              return disjunction;
            });

    return predicates;
  }

  /**
   * Get Predicate for checking Data Sharing access for given User's uid and UserGroup Uids
   *
   * @param builder CriteriaBuilder
   * @param userUid User Uid for checking access
   * @param userGroupUids List of UserGroup Uid which given user belong to
   * @param access Access String for checking
   * @return List of {@link Predicate}
   */
  public List<Function<Root<T>, Predicate>> getDataSharingPredicates(
          CriteriaBuilder builder, String userUid, Set<String> userGroupUids, String access) {
    List<Function<Root<T>, Predicate>> predicates = new ArrayList<>();

    preProcessPredicates(builder, predicates);

    Function<Root<T>, Predicate> userGroupPredicate =
            JpaQueryUtils.checkUserGroupsAccess(builder, userGroupUids, access);

    Function<Root<T>, Predicate> userPredicate =
            JpaQueryUtils.checkUserAccess(builder, userUid, access);

    predicates.add(
            root -> {
              Predicate disjunction =
                      builder.or(
                              builder.like(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.PUBLIC)),
                                      access),
                              builder.equal(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.PUBLIC)),
                                      "null"),
                              builder.isNull(
                                      builder.function(
                                              JsonbFunctions.EXTRACT_PATH_TEXT,
                                              String.class,
                                              root.get(BaseIdentifiableObject_.SHARING),
                                              builder.literal(Sharing_.PUBLIC))),
                              userPredicate.apply(root));

              Predicate ugPredicateWithRoot = userGroupPredicate.apply(root);

              if (ugPredicateWithRoot != null) {
                return builder.or(disjunction, ugPredicateWithRoot);
              }

              return disjunction;
            });

    return predicates;
  }

  @Override
  public List<Function<Root<T>, Predicate>> getDataSharingPredicates(
          CriteriaBuilder builder, User user) {
    return user == null
            ? List.of()
            : getDataSharingPredicates(
            builder,
            user,
            currentUserService.getCurrentUserGroupsInfo(user.getUid()),
            AclService.LIKE_READ_DATA);
  }

  /*
  @Override
  public List<Function<Root<T>, Predicate>> getSharingPredicates(
          CriteriaBuilder builder, CurrentUserDetails user, String access) {
    if (!sharingEnabled(user) || user == null) {
      return new ArrayList<>();
    }

    return getSharingPredicates(builder, user.getUid(), user.getUserGroupIds(), access);
  }
  */


  @Override
  public List<Function<Root<T>, Predicate>> getSharingPredicates(CriteriaBuilder builder) {
    return getSharingPredicates(
            builder, currentUserService.getCurrentUser(), AclService.LIKE_READ_METADATA);
  }





  @Override
  public List<Function<Root<T>, Predicate>> getSharingPredicates(
          CriteriaBuilder builder, User user) {
    return user == null
            ? List.of()
            : getSharingPredicates(
            builder,
            user,
            currentUserService.getCurrentUserGroupsInfo(user.getUid()),
            AclService.LIKE_READ_METADATA);
  }

  @Override
  public List<Function<Root<T>, Predicate>> getDataSharingPredicates(
          CriteriaBuilder builder, User user, CurrentUserGroupInfo groupInfo, String access) {
    List<Function<Root<T>, Predicate>> predicates = new ArrayList<>();

    if (user == null || !dataSharingEnabled(user) || groupInfo == null) {
      return predicates;
    }

    return getDataSharingPredicates(
            builder, groupInfo.getUserUID(), groupInfo.getUserGroupUIDs(), access);
  }

  @Override
  public List<Function<Root<T>, Predicate>> getDataSharingPredicates(
          CriteriaBuilder builder, User user, String access) {
    List<Function<Root<T>, Predicate>> predicates = new ArrayList<>();

    if (user == null || !dataSharingEnabled(user)) {
      return predicates;
    }

    Set<String> groupIds =
            user.getGroups().stream().map(g -> g.getUid()).collect(Collectors.toSet());

    return getDataSharingPredicates(builder, user.getUid(), groupIds, access);
  }

  protected boolean forceAcl() {
    return Dashboard.class.isAssignableFrom(clazz);
  }

  /**
   * @deprecated use {@link #sharingEnabled( CurrentUserDetails )} instead.
   */
  @Deprecated
  protected boolean sharingEnabled(User user) {
    boolean b = forceAcl();

    if (b) {
      return b;
    } else {
      return (aclService.isClassShareable(clazz) && !(user == null || user.isSuper()));
    }
  }

  protected boolean sharingEnabled(CurrentUserDetails user) {
    boolean b = forceAcl();

    if (b) {
      return b;
    } else {
      return (aclService.isClassShareable(clazz) && !(user == null || user.isSuper()));
    }
  }

  protected boolean dataSharingEnabled(CurrentUserDetails user) {
    return aclService.isDataClassShareable(clazz) && !user.isSuper();
  }

  /**
   * @deprecated use {@link #dataSharingEnabled( CurrentUserDetails )} instead.
   */
  @Deprecated
  private boolean dataSharingEnabled(User user) {
    return aclService.isDataClassShareable(clazz) && !user.isSuper();
  }


  @Override
  public final DetachedCriteria getSharingDetachedCriteria( User user )
  {
    return getSharingDetachedCriteria(  user, AclService.LIKE_READ_METADATA );
  }

  public final Criteria getDataSharingCriteria()
  {
    return getExecutableCriteria(
            getDataSharingDetachedCriteria( currentUserService.getCurrentUser(), AclService.LIKE_READ_DATA ) );
  }

  @Override
  public final DetachedCriteria getDataSharingDetachedCriteria( String access )
  {
    return getDataSharingDetachedCriteria( currentUserService.getCurrentUser(), access );
  }

  @Override
  public final DetachedCriteria getSharingDetachedCriteria( String access )
  {
    return getSharingDetachedCriteria( currentUserService.getCurrentUser(), access );
  }

  @Override
  public final DetachedCriteria getSharingDetachedCriteria()
  {
    return getSharingDetachedCriteria( currentUserService.getCurrentUser(), AclService.LIKE_READ_METADATA );
  }
  @Override
  public final DetachedCriteria getDataSharingDetachedCriteria( User user )
  {
    return getDataSharingDetachedCriteria(  user , AclService.LIKE_READ_DATA );
  }

  @Override
  public final Criteria getSharingCriteria( User user )
  {
    return getExecutableCriteria(
            getSharingDetachedCriteria(  user, AclService.LIKE_READ_METADATA ) );
  }

  @Override
  public final Criteria getSharingCriteria()
  {
    return getExecutableCriteria(
            getSharingDetachedCriteria( currentUserService.getCurrentUser(), AclService.LIKE_READ_METADATA ) );
//        return getExecutableCriteria(
//                getSharingDetachedCriteria( currentUserService.getCurrentUserGroupsInfo(), AclService.LIKE_READ_METADATA ) );
  }

  @Override
  public final List<Function<Root<T>, Predicate>> getDataSharingPredicates( CriteriaBuilder builder, String access )
  {
    return getDataSharingPredicates( builder, currentUserService.getCurrentUser(),
            currentUserService.getCurrentUserGroupsInfo(), access );
  }




  @Override
  public final List<Function<Root<T>, Predicate>> getDataSharingPredicates( CriteriaBuilder builder )
  {
    return getDataSharingPredicates( builder, currentUserService.getCurrentUser(),
            currentUserService.getCurrentUserGroupsInfo(), AclService.LIKE_READ_DATA );
  }

  @Override
  public  List<Function<Root<T>, Predicate>> getSharingPredicates( CriteriaBuilder builder, String access )
  {
    User user = currentUserService.getCurrentUser();
    return getSharingPredicates( builder, user, currentUserService.getCurrentUserGroupsInfo( user.getUid() ),
            access );
  }

  @Override
  public List<Function<Root<T>, Predicate>> getSharingPredicates( CriteriaBuilder builder, User user,
                                                                  CurrentUserGroupInfo groupInfo, String access )
  {
    if ( !sharingEnabled( user ) || user == null || groupInfo == null )
    {
      return new ArrayList<>();
    }

    return getSharingPredicates( builder, groupInfo.getUserUID(), groupInfo.getUserGroupUIDs(), access );
  }


  @Override
  public List<Function<Root<T>, Predicate>> getSharingPredicates( CriteriaBuilder builder, User user, String access )
  {
    if ( !sharingEnabled( user ) || user == null )
    {
      return new ArrayList<>();
    }

    Set<String> groupIds = user.getGroups().stream().map( g -> g.getUid() ).collect( Collectors.toSet() );

    return getSharingPredicates( builder, user.getUid(), groupIds, access );
  }

  private DetachedCriteria getSharingDetachedCriteria( User user , String access )
  {
    DetachedCriteria criteria = DetachedCriteria.forClass( getClazz(), "c" );

    preProcessDetachedCriteria( criteria );

    if ( !sharingEnabled( user ) || user == null )
    {
      return criteria;
    }

    Assert.notNull( user, "User argument can't be null." );

    Disjunction disjunction = Restrictions.disjunction();

    disjunction.add( Restrictions.like( "c.publicAccess", access ) );
    disjunction.add( Restrictions.isNull( "c.publicAccess" ) );
    disjunction.add( Restrictions.isNull( "c.user.id" ) );
    disjunction.add( Restrictions.eq( "c.user.id", user.getId() ) );

    DetachedCriteria userGroupDetachedCriteria = DetachedCriteria.forClass( getClazz(), "ugdc" );
    userGroupDetachedCriteria.createCriteria( "ugdc.userGroupAccesses", "uga" );
    userGroupDetachedCriteria.createCriteria( "uga.userGroup", "ug" );
    userGroupDetachedCriteria.createCriteria( "ug.members", "ugm" );

    userGroupDetachedCriteria.add( Restrictions.eqProperty( "ugdc.id", "c.id" ) );
    userGroupDetachedCriteria.add( Restrictions.eq( "ugm.id", user.getId() ) );
    userGroupDetachedCriteria.add( Restrictions.like( "uga.access", access ) );

    userGroupDetachedCriteria.setProjection( Property.forName( "uga.id" ) );

    disjunction.add( Subqueries.exists( userGroupDetachedCriteria ) );

    DetachedCriteria userDetachedCriteria = DetachedCriteria.forClass( getClazz(), "udc" );
    userDetachedCriteria.createCriteria( "udc.userAccesses", "ua" );
    userDetachedCriteria.createCriteria( "ua.user", "u" );

    userDetachedCriteria.add( Restrictions.eqProperty( "udc.id", "c.id" ) );
    userDetachedCriteria.add( Restrictions.eq( "u.id", user.getId() ) );
    userDetachedCriteria.add( Restrictions.like( "ua.access", access ) );

    userDetachedCriteria.setProjection( Property.forName( "ua.id" ) );

    disjunction.add( Subqueries.exists( userDetachedCriteria ) );

    criteria.add( disjunction );

    return criteria;
  }

  private DetachedCriteria getDataSharingDetachedCriteria( User user, String access )
  {
    DetachedCriteria criteria = DetachedCriteria.forClass( getClazz(), "c" );

    if ( user == null || !dataSharingEnabled( user ) )
    {
      return criteria;
    }

    Assert.notNull( user, "User argument can't be null." );

    Disjunction disjunction = Restrictions.disjunction();

    disjunction.add( Restrictions.like( "c.publicAccess", access ) );
    disjunction.add( Restrictions.isNull( "c.publicAccess" ) );

    DetachedCriteria userGroupDetachedCriteria = DetachedCriteria.forClass( getClazz(), "ugdc" );
    userGroupDetachedCriteria.createCriteria( "ugdc.userGroupAccesses", "uga" );
    userGroupDetachedCriteria.createCriteria( "uga.userGroup", "ug" );
    userGroupDetachedCriteria.createCriteria( "ug.members", "ugm" );

    userGroupDetachedCriteria.add( Restrictions.eqProperty( "ugdc.id", "c.id" ) );
    userGroupDetachedCriteria.add( Restrictions.eq( "ugm.id", user.getId() ) );
    userGroupDetachedCriteria.add( Restrictions.like( "uga.access", access ) );

    userGroupDetachedCriteria.setProjection( Property.forName( "uga.id" ) );

    disjunction.add( Subqueries.exists( userGroupDetachedCriteria ) );

    DetachedCriteria userDetachedCriteria = DetachedCriteria.forClass( getClazz(), "udc" );
    userDetachedCriteria.createCriteria( "udc.userAccesses", "ua" );
    userDetachedCriteria.createCriteria( "ua.user", "u" );

    userDetachedCriteria.add( Restrictions.eqProperty( "udc.id", "c.id" ) );
    userDetachedCriteria.add( Restrictions.eq( "u.id", user.getId() ) );
    userDetachedCriteria.add( Restrictions.like( "ua.access", access ) );

    userDetachedCriteria.setProjection( Property.forName( "ua.id" ) );

    disjunction.add( Subqueries.exists( userDetachedCriteria ) );

    criteria.add( disjunction );

    return criteria;
  }




}