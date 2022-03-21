<h1 align="start">Unicode</h1>
<h4 align="start">Application for searching, viewing and copying characters from Unicode 15</h1>

<img src="https://raw.githubusercontent.com/vadiole/Unicode/master/assets/Screenshot-1.png" alt="screenshot 1" width="24.6%" height="24%"> <img src="https://raw.githubusercontent.com/vadiole/Unicode/master/assets/Screenshot-2.png" alt="screenshot 2" width="24.6%" height="23%"> <img src="https://raw.githubusercontent.com/vadiole/Unicode/master/assets/Screenshot-3.png" alt="screenshot 2" width="24.6%" height="24%"> <img src="https://raw.githubusercontent.com/vadiole/Unicode/master/assets/Screenshot-4.png" alt="screenshot 4" width="24.6%" height="24%">


### Caution
I decided to develop an app without using the classic modern-android-development stack.
There is no mvvm or clean architecture, no dagger, no room, no app compat and material libraries, no fragments and navigation component, no constraint layout and basically no xml. The motivation is to learn to create applications with as few dependencies as possible. As for xml – I just don't like it. 

The Ui is in pure Kotlin. The navigation is made with views, the database is a plain sqlite. Dependencies injection is done via constructor, and async using coroutines (thinking about moving to executors).

### Design
I tried to make ui close to iOS in appearance and behavior using spring animations, squircle for roundings, self-written themes, etc. This is one of the reasons why I had to get rid of fragments and xml.

### Conclusion
This is an interesting experiment. I will be glad if you try the application, share with friends, leave feedback or find something useful for yourself in the source code.
