# Contributing to UserLAnd 
Thanks for throwing some effort into helping us improve our project! 
This is a set of guidelines to contributing to the UserLAnd Android application.
These are intended as just that: guidelines, so use your best judgement when submitting contributions.

All contributions must follow the UserLAnd [Code of Conduct](https://github.com/CypherpunkArmory/UserLAnd/blob/master/CODE_OF_CONDUCT.md).

## Connect with us
Please talk with us here in the form of a PR or an issue.
 
## Architecture
We follow the MVVM-C architecture in UserLAnd. UI updates should exist exclusively within XML and be inflated exclusively from
view-controllers like activies and fragments. Business logic should be decoupled from the Android framework as extensively 
as possible, and live somewhere with the `model` or `utils` package. Application layer logic should exist within the 
viewmodel as much as possible. 

## Steps to Follow

1. Submit an issue describing the problem you will be solving with your contribution if one doesn't exist. Assign yourself to the ticket.
2. Fork from master.
3. Write your code.
4. Write tests your code.
5. Run the test suite to ensure `./gradlew testAll`.
6. Ensure that your code follows our styling by running `./gradlew ktlint` at the root of the project.
7. Submit a pull request to master!

## Style Guides
**Commit Messages**
- Use present tense.
- Keep the first line short, but feel free to reference issues etc. in lines after.

**Branch Names**
- Reference issue number in branch name.
- Describe issue solved in branch name as briefly as possible.
