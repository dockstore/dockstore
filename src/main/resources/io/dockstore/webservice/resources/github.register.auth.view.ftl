<#-- @ftlvariable name="" type="io.consonance.webservice.resources.UserResource.GithubRegisterView" -->
<html>
    <body>
        <p>Client ID: ${parent.githubClientID?html}</p>
        <p>redirectURI: http://localhost:8080/user/registerGithubRedirect</p>

         <a href="https://github.com/login/oauth/authorize?redirect_uri=http://localhost:8080/user/registerGithubRedirect&client_id=${parent.githubClientID?html}&scope=repo:read,user:read">Authorization link</a> 
    </body>
</html>
