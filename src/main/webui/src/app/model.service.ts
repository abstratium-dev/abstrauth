import { Injectable, signal, Signal, WritableSignal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class ModelService {

  private signUpUsername = signal('')
  private signUpPassword = signal('')
  private signInRequestId = signal('')

  signUpUsername$: Signal<string> = this.signUpUsername.asReadonly()
  signUpPassword$: Signal<string> = this.signUpPassword.asReadonly()
  signInRequestId$: Signal<string> = this.signInRequestId.asReadonly()

  setSignUpUsername(username: string) {
    this.signUpUsername.set(username)
  }

  setSignUpPassword(password: string) {
    this.signUpPassword.set(password)
  }

  setSignInRequestId(requestId: string) {
    this.signInRequestId.set(requestId)
  }
}
