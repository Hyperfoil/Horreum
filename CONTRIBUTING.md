# 🧑‍💻 Contributor flow
There are some steps that you need to follow before making any contribution in this project, the following steps are important to get start.

**Note:** If you don't know how to set up this project locally? Please first Checkout the [README.md](./README.md) to set up the project locally and then follow the steps.

## Follow the Steps
- [Fork the repository](https://github.com/Hyperfoil/Horreum/fork)

- Clone the project locally 

``` 
git clone https://github.com/shivam-sharma7/Horreum.git 
``` 
**Note:** And in the place of `shivam-sharma7` you have to add your github username.

- Create a new branch

```
git checkout -b <your branch_name>
```

After creating new branch start making your changes and once the changes done then push your changes in your fork version of project which is know by default ` origin `

- Push your changes

```
git push origin <your branch_name>
```

Now you can check your change in your fork version and then create a ` pull_request` and wait for the review. The project maintainer will review your PR and then they will merged it. If you want to support, please give a ⭐

### Things to remember before making changes

Before making any contribution make sure your local master keep up-to-date with upstream master. To do that type the following commands.

- First add upstream
```
git remote add upstream https://github.com/Hyperfoil/Horreum.git
```
- Pull all changes from upstream
```
 git fetch upstream
```
- Keep your fork up-to-date
```
  git rebase -i upstream/master
```
- Creating a branch
```
git checkout -b myfeature master
```

### Code Style

Horreum has a strictly enforced code style. Code formatting is done by the Eclipse code formatter, using the config files found in the `config/` directory. By default, when you run `mvn install`, the code will
be formatted automatically. When submitting a pull request the CI build will fail if running the formatter results in any code changes, so it is recommended that you always run a full Maven build before submitting a pull request.

If you want to run the formatting without doing a full build, you can run `mvn process-sources`.

#### Eclipse Formatting Setup

Open the *Preferences* window, and then navigate to _Java_ -> _Code Style_ -> _Formatter_. Click _Import_ and then
select the `eclipse-formatting.xml` file in the `config/` directory.

Next navigate to _Java_ -> _Code Style_ -> _Organize Imports_. Click _Import_ and select the `eclipse.importorder` file.

#### IDEA Formatting Setup

Open the _Preferences_ window (or _Settings_ depending on your edition), navigate to _Plugins_ and install
the [Adapter for Eclipse Code Formatter](https://plugins.jetbrains.com/plugin/6546-eclipse-code-formatter) from the
Marketplace.

Restart your IDE, open the *Preferences* (or *Settings*) window again and navigate to _Adapter for Eclipse Code
Formatter_ section on the left pane.

Select _Use Eclipse's Code Formatter_, then change the _Eclipse workspace/project folder or config file_ to point to the
`eclipse-formatting.xml` file in the `config/` directory. Make sure the _Optimize Imports_ box is
ticked. Then, select _Import Order from file_ and make it point to the `eclipse.importorder` file in the `config/` 
directory.

Next, disable wildcard imports:
navigate to _Editor_ -> _Code Style_ -> _Java_ -> _Imports_
and set _Class count to use import with '\*'_ to `999`. Do the same with _Names count to use static import with '\*'_.

#### Git Formatting Setup

If you want to be sure that the _formatting_ is properly executed before committing, you can make use of the git hooks, in particular the `pre-commit` one.
Simply override the `.git/hooks/pre-commit` script with a custom logic that runs `mvn formatter:format impsort:sort` to format the source code and the remember to `git add` all changed files such that they are included in the ongoing commit.