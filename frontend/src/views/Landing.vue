<script setup>
import { ref } from 'vue'
const isSignup = ref(false)
</script>

<template>
    
  <div class="page" :class="{ signupMode: isSignup }">
    <header class="site-header">
      <div class="brand">
        <div class="logo">CSD</div>
        <span class="brand-name">AppName</span>
      </div>

      <nav class="site-header-links">
        <a href="#">Solutions</a>
        <a href="#">About Us</a>
      </nav>

      <button class="acc-btn">Account</button>
    </header>
    
    <main class="pagebody">
      
      <aside class="infopanel">
        <code>
          I am the infopanel. <br>
          sample text
        </code>
      </aside>

      <div class="login-card">
        <div class="login-inner">

      
          <template v-if="!isSignup">
            <h1>Let’s get you back in the field.</h1>

            <form class="login-form">
              <div class="login-field">
                <input id="username" type="text" placeholder=" " />
                <label for="username">Username / Email</label>
              </div>

              <div class="login-field">
                  <input id="password" type="password" placeholder=" " />
                <label for="password">Password</label>
              </div>

              <a href="#" class="forgot-link">Forgot your password?</a>
            
              <div class="actions">
                <button type="button" class="signup-btn" @click="isSignup = true">Sign Up</button>
                <button type="submit" class="login-btn">Login</button>
              </div>
            </form>
          </template>

          <template v-else>
            <h1>Create your account.</h1>

            <form class="login-form">
              <div class="login-field">
                <input id="new-username" type="text" placeholder=" " />
                <label for="new-username">Username</label>
              </div>

              <div class="login-field">
                <input id="email" type="email" placeholder=" " />
                <label for="email">Email</label>
              </div>

              <div class="login-field">
                <input id="new-password" type="password" placeholder=" " />
                <label for="new-password">Password</label>
              </div>

              <div class="actions">
                <button type="button" class="signup-btn" @click="isSignup = false">Back</button>
                <button type="submit" class="login-btn">Create Account</button>
              </div>
            </form>
          </template>

        </div>
      </div>
    </main>

    <section id="site-footer" aria-label="Resources">
      <ul class="bottom-bar">
    <!-- <li>
          <a href="https://x.com/" target="_blank" rel="noopener noreferrer">
            <svg class="button-icon" aria-hidden="true" viewBox="0 0 24 24">
              <use href="/icons.svg#x-icon"></use>
            </svg>
            <span>X.com</span>
          </a>
        </li>
        <li>
          <a href="https://bsky.app/" target="_blank" rel="noopener noreferrer">
            <svg class="button-icon" aria-hidden="true" viewBox="0 0 24 24">
              <use href="/icons.svg#bluesky-icon"></use>
            </svg>
            <span>Bluesky</span>
          </a>
        </li> 
    -->
        <li>
          <a href="https://github.com/annya-s/CSD-Project" target="_blank" rel="noopener noreferrer">
            <svg class="button-icon" aria-hidden="true" viewBox="0 0 24 24">
              <use href="/icons.svg#github-icon"></use>
            </svg>
            <span>GitHub</span>
          </a>
        </li>
      </ul>
      </section>
  </div>

</template>
