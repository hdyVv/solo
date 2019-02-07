/*
 * Solo - A small and beautiful blogging system written in Java.
 * Copyright (c) 2010-2019, b3log.org & hacpai.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.solo.processor.console;

import org.apache.commons.lang.StringEscapeUtils;
import org.b3log.latke.Keys;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.logging.Level;
import org.b3log.latke.logging.Logger;
import org.b3log.latke.model.Role;
import org.b3log.latke.model.User;
import org.b3log.latke.service.LangPropsService;
import org.b3log.latke.service.ServiceException;
import org.b3log.latke.servlet.RequestContext;
import org.b3log.latke.servlet.annotation.Before;
import org.b3log.latke.servlet.annotation.RequestProcessor;
import org.b3log.latke.servlet.renderer.JsonRenderer;
import org.b3log.solo.model.Option;
import org.b3log.solo.service.PreferenceQueryService;
import org.b3log.solo.service.UserMgmtService;
import org.b3log.solo.service.UserQueryService;
import org.b3log.solo.util.Solos;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * User console request processing.
 *
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://hacpai.com/member/DASHU">DASHU</a>
 * @version 1.2.1.5, Jan 10, 2019
 * @since 0.4.0
 */
@RequestProcessor
public class UserConsole {

    /**
     * Logger.
     */
    private static final Logger LOGGER = Logger.getLogger(UserConsole.class);

    /**
     * User query service.
     */
    @Inject
    private UserQueryService userQueryService;

    /**
     * User management service.
     */
    @Inject
    private UserMgmtService userMgmtService;

    /**
     * Preference query service.
     */
    @Inject
    private PreferenceQueryService preferenceQueryService;

    /**
     * Language service.
     */
    @Inject
    private LangPropsService langPropsService;

    /**
     * Updates a user by the specified request.
     *
     * <p>
     * Request json:
     * <pre>
     * {
     *     "oId": "",
     *     "userName": "",
     *     "userEmail": "",
     *     "userRole": "", // optional
     *     "userURL": "", // optional
     *     "userAvatar": "" // optional
     * }
     * </pre>
     * </p>
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified http request context
     */
    @Before(ConsoleAdminAuthAdvice.class)
    public void updateUser(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        context.setRenderer(renderer);
        final JSONObject ret = new JSONObject();

        try {
            final JSONObject requestJSONObject = context.requestJSON();
            userMgmtService.updateUser(requestJSONObject);

            ret.put(Keys.STATUS_CODE, true);
            ret.put(Keys.MSG, langPropsService.get("updateSuccLabel"));
            renderer.setJSONObject(ret);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, e.getMessage());
        }
    }

    /**
     * Adds a user with the specified request.
     *
     * <p>
     * Request json:
     * <pre>
     * {
     *     "userName": "",
     *     "userEmail": "",
     *     "userURL": "", // optional, uses 'servePath' instead if not specified
     *     "userRole": "", // optional, uses {@value org.b3log.latke.model.Role#DEFAULT_ROLE} instead if not specified
     *     "userAvatar": "" // optional
     * }
     * </pre>
     * </p>
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "oId": "", // Generated user id
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified http request context
     */
    public void addUser(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        context.setRenderer(renderer);
        final JSONObject ret = new JSONObject();
        renderer.setJSONObject(ret);

        try {
            final JSONObject requestJSONObject = context.requestJSON();
            if (Solos.isAdminLoggedIn(context)) { // if the administrator register a new user, treats the new user as a normal user
                // (defaultRole) who could post article
                requestJSONObject.put(User.USER_ROLE, Role.DEFAULT_ROLE);
            } else {
                final JSONObject preference = preferenceQueryService.getPreference();
                if (!preference.optBoolean(Option.ID_C_ALLOW_REGISTER)) {
                    ret.put(Keys.STATUS_CODE, false);
                    ret.put(Keys.MSG, langPropsService.get("notAllowRegisterLabel"));

                    return;
                }

                // if a normal user or a visitor register a new user, treats the new user as a visitor
                // (visitorRole) who couldn't post article
                requestJSONObject.put(User.USER_ROLE, Role.VISITOR_ROLE);
            }

            final String userId = userMgmtService.addUser(requestJSONObject);

            ret.put(Keys.OBJECT_ID, userId);
            ret.put(Keys.MSG, langPropsService.get("addSuccLabel"));
            ret.put(Keys.STATUS_CODE, true);
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, e.getMessage());
        }
    }

    /**
     * Removes a user by the specified request.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified http request context
     */
    @Before(ConsoleAdminAuthAdvice.class)
    public void removeUser(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        context.setRenderer(renderer);
        final JSONObject jsonObject = new JSONObject();
        renderer.setJSONObject(jsonObject);
        try {
            final String userId = context.pathVar("id");
            userMgmtService.removeUser(userId);

            jsonObject.put(Keys.STATUS_CODE, true);
            jsonObject.put(Keys.MSG, langPropsService.get("removeSuccLabel"));
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            jsonObject.put(Keys.STATUS_CODE, false);
            jsonObject.put(Keys.MSG, langPropsService.get("removeFailLabel"));
        }
    }

    /**
     * Gets users by the specified request json object.
     * <p>
     * The request URI contains the pagination arguments. For example, the request URI is /console/users/1/10/20, means
     * the current page is 1, the page size is 10, and the window size is 20.
     * </p>
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "pagination": {
     *         "paginationPageCount": 100,
     *         "paginationPageNums": [1, 2, 3, 4, 5]
     *     },
     *     "users": [{
     *         "oId": "",
     *         "userName": "",
     *         "userEmail": "",
     *         "roleName": ""
     *      }, ....]
     *     "sc": true
     * }
     * </pre>
     * </p>
     *
     * @param context the specified http request context
     */
    @Before(ConsoleAdminAuthAdvice.class)
    public void getUsers(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        context.setRenderer(renderer);

        try {
            final String requestURI = context.requestURI();
            final String path = requestURI.substring((Latkes.getContextPath() + "/console/users/").length());
            final JSONObject requestJSONObject = Solos.buildPaginationRequest(path);
            final JSONObject result = userQueryService.getUsers(requestJSONObject);
            result.put(Keys.STATUS_CODE, true);
            renderer.setJSONObject(result);

            final JSONArray users = result.optJSONArray(User.USERS);
            for (int i = 0; i < users.length(); i++) {
                final JSONObject user = users.optJSONObject(i);
                String userName = user.optString(User.USER_NAME);
                userName = StringEscapeUtils.escapeXml(userName);
                user.put(User.USER_NAME, userName);
            }
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("getFailLabel"));
        }
    }

    /**
     * Gets a user by the specified request.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "user": {
     *         "oId": "",
     *         "userName": "",
     *         "userEmail": "",
     *         "userAvatar": ""
     *     }
     * }
     * </pre>
     * </p>
     *
     * @param context the specified http request context
     */
    @Before(ConsoleAdminAuthAdvice.class)
    public void getUser(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        context.setRenderer(renderer);
        final String userId = context.pathVar("id");

        final JSONObject result = userQueryService.getUser(userId);
        if (null == result) {
            final JSONObject jsonObject = new JSONObject().put(Keys.STATUS_CODE, false);
            renderer.setJSONObject(jsonObject);
            jsonObject.put(Keys.MSG, langPropsService.get("getFailLabel"));

            return;
        }

        renderer.setJSONObject(result);
        result.put(Keys.STATUS_CODE, true);
    }

    /**
     * Change a user role.
     * <p>
     * Renders the response with a json object, for example,
     * <pre>
     * {
     *     "sc": boolean,
     *     "msg": ""
     * }
     * </pre>
     * </p>
     *
     * @param context the specified http request context
     */
    @Before(ConsoleAdminAuthAdvice.class)
    public void changeUserRole(final RequestContext context) {
        final JsonRenderer renderer = new JsonRenderer();
        context.setRenderer(renderer);
        final JSONObject jsonObject = new JSONObject();
        renderer.setJSONObject(jsonObject);
        try {
            final String userId = context.pathVar("id");
            userMgmtService.changeRole(userId);

            jsonObject.put(Keys.STATUS_CODE, true);
            jsonObject.put(Keys.MSG, langPropsService.get("updateSuccLabel"));
        } catch (final ServiceException e) {
            LOGGER.log(Level.ERROR, e.getMessage(), e);

            jsonObject.put(Keys.STATUS_CODE, false);
            jsonObject.put(Keys.MSG, langPropsService.get("removeFailLabel"));
        }
    }
}
