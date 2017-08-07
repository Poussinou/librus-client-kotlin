package com.wabadaba.dziennik.api

import android.annotation.SuppressLint
import android.content.Context
import android.preference.PreferenceManager
import io.reactivex.Observable
import io.reactivex.subjects.BehaviorSubject

@SuppressLint("ApplySharedPref")
class UserRepository(
        context: Context) {
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val usersPrefKey = "logged_in_users"

    private val userSubject: BehaviorSubject<FullUser> = BehaviorSubject.create<FullUser>()
    private val allUsersSubject: BehaviorSubject<List<User>> = BehaviorSubject.create<List<User>>()

    val currentUser: Observable<FullUser> = userSubject
    val allUsers: Observable<List<User>> = allUsersSubject

    init {
        val users = loadUsers()
        allUsersSubject.onNext(users)
        if (users.isNotEmpty()) {
            val defaultUser = users[0]
            val defaultUserFull = getFullUser(defaultUser)
            userSubject.onNext(defaultUserFull)
        }
    }

    private fun saveUsers(users: List<User>) {
        //serialize users as a string set
        val rawUsers = users
                .map { Parser.mapper.writeValueAsString(it) }
                .toSet()
        //save to prefs
        prefs.edit()
                .putStringSet(usersPrefKey, rawUsers)
                .commit()
        //update subject
        allUsersSubject.onNext(users)
    }

    fun loadUsers(): List<User> {
        val loadedRawUsers = prefs.getStringSet(usersPrefKey, null)
        if (loadedRawUsers != null) {
            //deserialize raw users
            return loadedRawUsers.map { Parser.parse(it, User::class) }
        } else {
            //if there is no saved data return empty list
            return emptyList()
        }
    }

    fun addUser(fullUser: FullUser) {
        //check if there already is a user with the same login
        val currentUsers = loadUsers().map { it.login }
        if (fullUser.login in currentUsers) {
            throw IllegalStateException("User ${fullUser.login} is already logged in!")
        }

        //add user to the current users
        val newUsers =
                listOf(
                        User(fullUser.login,
                                fullUser.firstName,
                                fullUser.lastName,
                                fullUser.groupId)) +
                        loadUsers()
        saveUsers(newUsers)

        saveAuthInfo(fullUser.login, fullUser.authInfo)

        //set the newly added user as current
        userSubject.onNext(fullUser)
    }

    fun removeUser(login: String) {
        deleteAuthInfo(login)

        val users = loadUsers()

        val newUsers = users.filter { it.login != login }
        saveUsers(newUsers)

        if (newUsers.isNotEmpty()) {
            switchUser(newUsers[0].login)
        }
    }

    /**
     * Switch to another user(must be already logged in)
     */
    fun switchUser(login: String) {
        val user = getUser(login)
        val fullUser = getFullUser(user)
        userSubject.onNext(fullUser)
    }

    private fun getUser(login: String): User {
        val users = loadUsers()
        val user = (users.filter { it.login == login }.singleOrNull()
                ?: throw UnsupportedOperationException("User $login doesn't exist."))
        return user
    }

    private fun getFullUser(user: User) = FullUser(
            user.login,
            user.firstName,
            user.lastName,
            user.groupId,
            loadAuthInfo(user.login))

    private fun authInfoKey(login: String) = "${login}_auth_info"

    private fun loadAuthInfo(login: String): AuthInfo {
        val stringValue = prefs.getString(authInfoKey(login), null)
                ?: throw IllegalStateException("Authorization info for user $login not found")
        return Parser.parse(stringValue, AuthInfo::class)
    }

    private fun saveAuthInfo(login: String, authInfo: AuthInfo) {
        val stringValue = Parser.mapper.writeValueAsString(authInfo)
        prefs.edit()
                .putString(authInfoKey(login), stringValue)
                .commit()
    }

    private fun deleteAuthInfo(login: String) {
        prefs.edit()
                .remove(authInfoKey(login))
                .commit()
    }
}