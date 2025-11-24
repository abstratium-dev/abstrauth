import { Injectable, signal } from '@angular/core';

@Injectable({
  providedIn: 'root',
})
export class ModelService {

  signInUsername$ = signal('')
  signInPassword$ = signal('')
  signInRequestId$ = signal('')

  setSignInUsername(username: string) {
    this.signInUsername$.set(username)
  }

  setSignInPassword(password: string) {
    this.signInPassword$.set(password)
  }

  setSignInRequestId(requestId: string) {
    this.signInRequestId$.set(requestId)
  }
}
