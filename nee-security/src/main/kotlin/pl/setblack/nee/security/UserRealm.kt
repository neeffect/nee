package pl.setblack.nee.security

import io.vavr.control.Option

interface UserRealm<USERID, ROLE> {
    fun loginUser( userLogin : String, password : CharArray)  : Option<USERID>

    fun hasRole(user : USERID, role : ROLE) : Boolean
}



